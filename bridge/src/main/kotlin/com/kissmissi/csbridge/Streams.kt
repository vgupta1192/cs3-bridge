package com.kissmissi.csbridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.Base64

/**
 * Formatter selection from the install URL (`fmt` key): a preset id, a custom
 * template pair, or built-in naming. Legacy string configs still decode —
 * old {provider}/{quality}/{link} tokens are translated to AIOStreams fields,
 * so pre-formatter install URLs keep working unchanged.
 */
data class FmtCfg(val f: String = "builtin", val n: String? = null, val d: String? = null) {
    companion object {
        fun fromRaw(raw: Any?): FmtCfg = when (raw) {
            is Map<*, *> -> FmtCfg(
                (raw["f"] as? String) ?: "builtin",
                raw["n"] as? String,
                raw["d"] as? String,
            )
            is String -> fromLegacy(raw)
            else -> FmtCfg()
        }

        private fun fromLegacy(s: String): FmtCfg = when {
            s == "modern" -> FmtCfg("csb-modern")
            s == "minimal" -> FmtCfg("csb-minimal")
            s.startsWith("custom:") -> {
                val parts = s.removePrefix("custom:").split("|", limit = 2)
                FmtCfg("custom", legacyTpl(parts.getOrElse(0) { "" }), legacyTpl(parts.getOrElse(1) { "" }))
            }
            else -> FmtCfg() // "original" / unknown = built-in naming
        }

        private fun legacyTpl(t: String): String = t
            .replace("{provider}", "{stream.provider}")
            .replace("{quality}", "{stream.resolution}")
            .replace("{link}", "{stream.filename}")
    }
}

class BridgeConfig(
    val providers: Set<String>,
    val catalogs: Boolean,
    val magnets: Boolean,
    val deadlineMs: Long? = null,
    val qualities: Set<Int> = setOf(2160, 1080, 720, 480, 360),
    val maxPerTier: Int = 0,
    val blockCam: Boolean = false,
    val fmt: FmtCfg = FmtCfg(),
    val order: List<String> = emptyList(),
) {
    companion object {
        val CORE_REPOS = setOf("CNC Repo (All Language)", "Phisher Repo", "Megix Repo (Hindi & English)", "raghav repo")

        fun decode(s: String): BridgeConfig {
            return try {
                val fixed = s.replace('-', '+').replace('_', '/') + "=".repeat((4 - s.length % 4) % 4)
                val json = String(Base64.getDecoder().decode(fixed))
                val root = jacksonObjectMapper().readValue<Map<String, Any>>(json)
                val p = (root["p"] as? Map<*, *>)?.filterValues { (it as? Number)?.toInt() != 0 }
                    ?.keys?.map { it.toString() }?.toSet() ?: emptySet()
                val q = root["q"] as? Map<*, *>
                val qual = (q?.get("on") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet()
                    ?: setOf(2160, 1080, 720, 480, 360)
                BridgeConfig(
                    p, (root["c"] as? Number)?.toInt() == 1, (root["m"] as? Number)?.toInt() == 1,
                    (root["d"] as? Number)?.toLong(),
                    qual,
                    (q?.get("tier") as? Number)?.toInt() ?: 0,
                    (q?.get("cam") as? Number)?.toInt() == 1,
                    FmtCfg.fromRaw(root["fmt"]),
                    (root["order"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                )
            } catch (e: Exception) {
                BridgeConfig(
                    Repos.plugins.values.filter { it.loaded && it.repo in CORE_REPOS }.map { it.internalName }.toSet(),
                    true, false, null
                )
            }
        }
    }
}

object Streams {
    private val mapper = jacksonObjectMapper()

    // Detached scope: scraping outlives the HTTP request so slow providers never
    // block the response; whatever arrives before the deadline is returned.
    private val scrapeDispatcher = Dispatchers.IO.limitedParallelism(128)
    private val scrapeScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + scrapeDispatcher)

    fun b64(s: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
    fun unb64(s: String): String {
        val padded = s + "=".repeat((4 - s.length % 4) % 4)
        return String(Base64.getUrlDecoder().decode(padded))
    }


    fun norm(s: String): String = s.lowercase()
        .replace(Regex("""\[.*?\]|\(.*?\)"""), " ")
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun yearOf(r: com.lagradost.cloudstream3.SearchResponse): Int? = when (r) {
        is MovieSearchResponse -> r.year
        is TvSeriesSearchResponse -> r.year
        else -> null
    }


    private fun matches(r: com.lagradost.cloudstream3.SearchResponse, title: String, year: Int?): Boolean {
        val a = norm(r.name); val b = norm(title)
        if (a.isEmpty() || b.isEmpty()) return false
        val score = FuzzySearch.weightedRatio(a, b)
        val contains = a.contains(b) || b.contains(a)
        if (score < 80 && !contains) return false
        val ry = yearOf(r)
        if (ry != null && year != null && Math.abs(ry - year) > 1) return false
        return true
    }


    private fun qualityLabel(q: Int): String = when {
        q >= 2160 -> "4K"
        q >= 1440 -> "1440p"
        q >= 1080 -> "1080p"
        q >= 720 -> "720p"
        q >= 480 -> "480p"
        q >= 360 -> "360p"
        q > 0 -> "${q}p"
        else -> ""
    }

    private val camRegex = Regex("(?i)\\b(cam|hdcam|hd-cam|hdts|telecine|telesync|screener|ts)\\b")

    private fun isCam(link: ExtractorLink): Boolean =
        camRegex.containsMatchIn(link.name) || camRegex.containsMatchIn(link.source) || (link.quality in 1..479 && camRegex.containsMatchIn(link.url))

    private fun tierOf(link: ExtractorLink): Int = when {
        link.quality >= 2160 -> 2160
        link.quality >= 1080 -> 1080
        link.quality >= 720 -> 720
        link.quality >= 480 -> 480
        link.quality > 0 -> 360
        else -> 0
    }

    /** Per-request formatter context, resolved while providers scrape. */
    data class FmtCtx(val kind: String, val season: Int?, val episode: Int?, val title: String?, val year: Int?)

    /** Built-in naming (no formatter configured or formatter failed). */
    fun toStremioStream(link: ExtractorLink, providerName: String, cfg: BridgeConfig): Map<String, Any?>? {
        if (link.url.isBlank()) return null
        if (link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET) {
            if (!cfg.magnets) return null
            return linkedMapOf(
                "name" to "CSB torrent",
                "title" to "$providerName • torrent",
                "description" to "$providerName • ${link.url.take(80)}",
                "externalUrl" to link.url,
            )
        }
        val headers = runCatching { link.getAllHeaders().filter { it.value.isNotBlank() } }.getOrDefault(emptyMap())
        val q = qualityLabel(link.quality)
        val firstLine = if (q.isBlank()) "CSB" else "CSB $q"
        val stream = linkedMapOf<String, Any?>(
            "name" to firstLine,
            "title" to "$providerName • ${link.name}",
            "description" to "$providerName • ${link.name}",
            "url" to link.url,
        )
        val hints = linkedMapOf<String, Any?>()
        if (headers.isNotEmpty()) hints["proxyHeaders"] = mapOf("request" to headers)
        if (link.type == ExtractorLinkType.DASH) hints["notWebReady"] = true
        if (hints.isNotEmpty()) stream["behaviorHints"] = hints
        return stream
    }

    private fun metaJson(link: ExtractorLink, provider: String): String = mapper.writeValueAsString(
        linkedMapOf<String, Any?>(
            "provider" to provider,
            "linkName" to link.name,
            "source" to link.source,
            "quality" to link.quality,
            "url" to link.url,
            "kind" to link.type.name,
        )
    )

    private fun ctxJson(ctx: FmtCtx?): String = mapper.writeValueAsString(
        linkedMapOf<String, Any?>(
            "mediaType" to (ctx?.kind ?: "movie"),
            "title" to ctx?.title,
            "year" to ctx?.year,
            "seasonNum" to ctx?.season,
            "episodeNum" to ctx?.episode,
        )
    )

    /** AIOStreams-formatted stream map from a batch-rendered entry. */
    private fun streamFromRendered(out: Formatter.Rendered, link: ExtractorLink, provider: String): Map<String, Any?> {
        val headers = runCatching { link.getAllHeaders().filter { it.value.isNotBlank() } }.getOrDefault(emptyMap())
        val stream = linkedMapOf<String, Any?>(
            "name" to out.name,
            "title" to out.description,
            "description" to out.description,
            "url" to link.url,
        )
        val hints = linkedMapOf<String, Any?>()
        if (headers.isNotEmpty()) hints["proxyHeaders"] = mapOf("request" to headers)
        if (link.type == ExtractorLinkType.DASH) hints["notWebReady"] = true
        if (hints.isNotEmpty()) stream["behaviorHints"] = hints
        return stream
    }


    /** Collect links out of a loaded response. */
    private suspend fun linksFromResponse(
        provider: MainAPI,
        resp: LoadResponse?,
        season: Int?,
        episode: Int?,
    ): List<ExtractorLink> {
        val data: String? = when (resp) {
            is MovieLoadResponse -> resp.dataUrl
            is TvSeriesLoadResponse -> {
                val eps = resp.episodes
                when {
                    eps.isEmpty() -> null
                    season == null && episode == null -> eps.first().data
                    else -> (eps.firstOrNull { it.season == season && it.episode == episode }?.data
                        ?: if (season == null) eps.firstOrNull { it.episode == episode }?.data else null)
                }
            }
            else -> null
        }
        if (data.isNullOrEmpty()) return emptyList()
        val out = ArrayList<ExtractorLink>()
        runCatching {
            provider.loadLinks(
                data = data,
                isCasting = false,
                subtitleCallback = { },
                callback = { link -> synchronized(out) { out.add(link) } }
            )
        }.onFailure { if (it is CancellationException) throw it }
        return out
    }


    /** Per-provider outcome: links, or the error that made the scrape fail (retryable). */
    class ProviderOutcome(val links: List<ExtractorLink>, val error: Throwable? = null)

    private suspend fun scrapeProvider(
        prov: MainAPI,
        titleInfo: TitleInfo?,
        season: Int?,
        episode: Int?,
        matchUrl: String? = null,
    ): ProviderOutcome = withTimeout(Cfg.providerTimeoutMs) {
        val url = matchUrl ?: run {
            val results = try {
                prov.search(titleInfo?.name ?: "", 1)?.items ?: prov.search(titleInfo?.name ?: "") ?: emptyList()
            } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
                return@withTimeout ProviderOutcome(emptyList(), t)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                return@withTimeout ProviderOutcome(emptyList(), t)
            }
            results.firstOrNull { matches(it, titleInfo?.name ?: "", titleInfo?.year) }?.url
        } ?: return@withTimeout ProviderOutcome(emptyList())
        val loaded = try {
            prov.load(url)
        } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
            return@withTimeout ProviderOutcome(emptyList(), t)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            return@withTimeout ProviderOutcome(emptyList(), t)
        } ?: return@withTimeout ProviderOutcome(emptyList(), IllegalStateException("load returned null"))
        try {
            ProviderOutcome(linksFromResponse(prov, loaded, season, episode))
        } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
            ProviderOutcome(emptyList(), t)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            ProviderOutcome(emptyList(), t)
        }
    }


    /** cacheKey -> a scrape/rescrape for this key is currently running (dedupes rescrapes against live scrapes). */
    private val activeScrapes = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Background re-scrape: for a cache entry written before its scrape finished
     * ("incomplete"), or a degraded low-stream entry old enough to be worth
     * refreshing (see [maybeRescrapeDegraded]). Deduped per cache key and against
     * a still-running scrape for the same key; overwrites the cache with the full
     * merged list when done.
     */
    fun rescrapeAsync(cfg: BridgeConfig, kind: String, id: String, cacheKey: String) {
        if (activeScrapes.putIfAbsent(cacheKey, true) != null) return
        scrapeScope.launch {
            try {
                streamsForInternal(cfg, kind, id, cacheKey, respond = false)
            } catch (t: Throwable) {
                AppLogger.i("Streams: rescrape failed $kind/$id: ${t.message}")
            } finally {
                activeScrapes.remove(cacheKey)
            }
        }
    }

    /**
     * Self-heal for degraded entries: an entry with very few streams that has sat
     * for a while gets one background rescrape (bounded by Cfg.rescrapeMinAgeMs
     * between rescrapes per key, since the rescrape rewrites the timestamp).
     * Without this, an entry scraped during a provider outage/rate-limit window
     * would serve its thin result as final for the whole cache TTL.
     */
    fun maybeRescrapeDegraded(cfg: BridgeConfig, kind: String, id: String, cacheKey: String, entry: Map<String, Any?>) {
        val count = (entry["streams"] as? List<*>)?.size ?: 0
        if (count >= Cfg.rescrapeMinStreams) return
        val ts = (entry["ts"] as? Number)?.toLong() ?: 0L
        if (ts > 0L && System.currentTimeMillis() - ts < Cfg.rescrapeMinAgeMs) return
        AppLogger.i("Streams: degraded cache entry $kind/$id ($count streams), rescraping in background")
        rescrapeAsync(cfg, kind, id, cacheKey)
    }

    /** Main entry: streams for a stremio-style request. Returns after the deadline; scraping continues detached. */
    fun streamsFor(cfg: BridgeConfig, kind: String, id: String, cacheKey: String? = null): List<Map<String, Any?>> =
        streamsForInternal(cfg, kind, id, cacheKey, respond = true)

    private fun streamsForInternal(cfg: BridgeConfig, kind: String, id: String, cacheKey: String?, respond: Boolean): List<Map<String, Any?>> {
        val t0 = System.currentTimeMillis()
        val deadline = cfg.deadlineMs ?: Cfg.deadlineMs
        val channel = Channel<Pair<String, List<ExtractorLink>>>(Channel.UNLIMITED)
        val collected = ArrayList<Pair<String, List<ExtractorLink>>>()
        val ctxRef = java.util.concurrent.atomic.AtomicReference<FmtCtx?>(null)

        if (cacheKey != null) activeScrapes[cacheKey] = true
        val parent = scrapeScope.launch {
            fetchAll(cfg, kind, id, channel, ctxRef)
            channel.close()
        }
        parent.invokeOnCompletion { if (cacheKey != null) activeScrapes.remove(cacheKey) }

        runBlocking {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < deadline) {
                val remaining = deadline - (System.currentTimeMillis() - start)
                val r = withTimeoutOrNull(remaining) { channel.receiveCatching() } ?: break
                val got = r.getOrNull() ?: break
                synchronized(collected) { collected.add(got) }
            }
        }

        val result = buildResult(cfg, collected, ctxRef.get())
        AppLogger.i("Streams: $kind/$id -> ${result.size} streams in ${System.currentTimeMillis() - t0} ms (scrape complete=${parent.isCompleted})")

        if (cacheKey != null) {
            // cache what we have now, marking whether stragglers are still running
            runCatching {
                Store.put(cacheKey, mapper.writeValueAsString(mapOf("streams" to result, "incomplete" to !parent.isCompleted, "ts" to System.currentTimeMillis())))
            }
        }

        // slow providers keep scraping in the background; when they finish, merge
        // their late results into the cache so the next tap shows the full list
        if (cacheKey != null) {
            scrapeScope.launch {
                runCatching {
                    val start = System.currentTimeMillis()
                    while (!parent.isCompleted && System.currentTimeMillis() - start < 600_000) {
                        val r = withTimeoutOrNull(30_000) { channel.receiveCatching() } ?: break
                        val got = r.getOrNull() ?: break
                        synchronized(collected) { collected.add(got) }
                    }
                    val full = buildResult(cfg, collected, ctxRef.get())
                    if (full.isNotEmpty() || respond) {
                        // a 30s lull can break the loop while the parent scrape is
                        // still running - keep the incomplete flag honest so the
                        // next request can still trigger one deduped rescrape
                        Store.put(cacheKey, mapper.writeValueAsString(mapOf("streams" to full, "incomplete" to !parent.isCompleted, "ts" to System.currentTimeMillis())))
                        AppLogger.i("Streams: $kind/$id late merge -> ${full.size} streams (cache updated, scrape complete=${parent.isCompleted})")
                    }
                }
            }
        }
        return result
    }

    private fun buildResult(cfg: BridgeConfig, collected: List<Pair<String, List<ExtractorLink>>>, ctx: FmtCtx?): List<Map<String, Any?>> {
        data class Row(val stream: Map<String, Any?>, val label: String, val link: ExtractorLink)
        // one templates lookup per serve; null = built-in naming everywhere
        val templates = runCatching { Formatter.templates(cfg.fmt.f, cfg.fmt.n, cfg.fmt.d) }.getOrNull()
        val rank = cfg.order.withIndex().associate { it.value.lowercase() to it.index }
        data class Cand(val label: String, val link: ExtractorLink)
        val candidates = ArrayList<Cand>()
        val seen = HashSet<String>()
        for ((label, links) in collected) {
            for (link in links) {
                if (cfg.blockCam && isCam(link)) continue
                val tier = tierOf(link)
                if (tier != 0 && tier !in cfg.qualities) continue
                if (seen.add(link.url)) candidates.add(Cand(label, link))
            }
        }
        // format the whole serve in ONE polyglot hop (torrent/magnet links keep the
        // built-in externalUrl handling; batch entries that fail = built-in naming)
        val formatted = HashMap<Int, Formatter.Rendered?>()
        if (templates != null) {
            val idx = ArrayList<Int>()
            val items = ArrayList<Pair<String, String>>()
            candidates.forEachIndexed { i, cand ->
                if (cand.link.type != ExtractorLinkType.TORRENT && cand.link.type != ExtractorLinkType.MAGNET && cand.link.url.isNotBlank()) {
                    idx.add(i)
                    items.add(metaJson(cand.link, cand.label) to ctxJson(ctx))
                }
            }
            if (items.isNotEmpty()) {
                val rendered = runCatching { Formatter.renderBatch(templates, items) }.getOrElse { emptyList() }
                rendered.forEachIndexed { j, r -> formatted[idx[j]] = r }
            }
        }
        var rows = ArrayList<Row>()
        candidates.forEachIndexed { i, cand ->
            val st = if (cand.link.type == ExtractorLinkType.TORRENT || cand.link.type == ExtractorLinkType.MAGNET) {
                toStremioStream(cand.link, cand.label, cfg)
            } else {
                formatted[i]?.let { streamFromRendered(it, cand.link, cand.label) } ?: toStremioStream(cand.link, cand.label, cfg)
            } ?: return@forEachIndexed
            rows.add(Row(st, cand.label, cand.link))
        }
        rows.sortBy { rank[it.label.lowercase()] ?: 1000 }
        if (cfg.maxPerTier > 0) {
            val counts = HashMap<Int, Int>()
            rows = ArrayList(rows.filter {
                val t = tierOf(it.link)
                if (t == 0) true else run { val n = (counts[t] ?: 0) + 1; counts[t] = n; n <= cfg.maxPerTier }
            })
        }
        return rows.map { it.stream }
    }

    private suspend fun fetchAll(
        cfg: BridgeConfig,
        kind: String,
        id: String,
        channel: Channel<Pair<String, List<ExtractorLink>>>,
        ctxRef: java.util.concurrent.atomic.AtomicReference<FmtCtx?>,
    ) = kotlinx.coroutines.coroutineScope {
        val sem = Semaphore(Cfg.maxConcurrent)
        val attempted = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val succeeded = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val noContent = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val errorSamples = java.util.concurrent.ConcurrentHashMap<String, String>()
        val retryDefs = java.util.concurrent.ConcurrentHashMap<String, Pair<String, suspend () -> ProviderOutcome>>()

        fun launchProvider(label: String, key: String, work: suspend () -> ProviderOutcome) {
            attempted.add(key)
            this@coroutineScope.launch(scrapeDispatcher) {
                try {
                    sem.acquire()
                    try {
                        val res = try {
                            work()
                        } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
                            ProviderOutcome(emptyList(), t)
                        } catch (t: CancellationException) {
                            throw t
                        } catch (t: Throwable) {
                            ProviderOutcome(emptyList(), t)
                        }
                        if (res.links.isNotEmpty()) {
                            succeeded.add(key)
                            channel.send(label to res.links)
                        } else if (res.error != null) {
                            failed.add(key)
                            errorSamples.putIfAbsent(label, "${res.error::class.java.simpleName}: ${res.error.message?.take(120) ?: "no message"}")
                        } else {
                            noContent.add(key)
                        }
                    } finally {
                        sem.release()
                    }
                } catch (_: Throwable) {
                }
            }
        }


        if (id.startsWith("csb:")) {
            val parts = id.split(":")
            if (parts.size >= 3) {
                val provName = parts[1]
                val url = runCatching { unb64(parts[2]) }.getOrNull()
                val requestedS = parts.getOrNull(3)?.toIntOrNull()
                val requestedE = parts.getOrNull(4)?.toIntOrNull()
                ctxRef.set(FmtCtx(kind, requestedS, requestedE, null, null))
                if (url != null) {
                    for ((info, provs) in Repos.enabledProviders(cfg.providers)) {
                        if (info.internalName != provName) continue
                        for (prov in provs) {
                            launchProvider(info.name, info.internalName + "#" + System.identityHashCode(prov)) {
                                val loaded = try {
                                    prov.load(url)
                                } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
                                    return@launchProvider ProviderOutcome(emptyList(), t)
                                } catch (t: CancellationException) {
                                    throw t
                                } catch (t: Throwable) {
                                    return@launchProvider ProviderOutcome(emptyList(), t)
                                } ?: return@launchProvider ProviderOutcome(emptyList(), IllegalStateException("load returned null"))
                                try {
                                    ProviderOutcome(linksFromResponse(prov, loaded, requestedS, requestedE))
                                } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
                                    ProviderOutcome(emptyList(), t)
                                } catch (t: CancellationException) {
                                    throw t
                                } catch (t: Throwable) {
                                    ProviderOutcome(emptyList(), t)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val imdb = id.substringBefore(":")
            val requestedS = id.split(":").getOrNull(1)?.toIntOrNull()
            val requestedE = id.split(":").getOrNull(2)?.toIntOrNull()
            val titleInfo = Resolver.resolve(kind, imdb)
            if (titleInfo == null) {
                AppLogger.i("Streams: could not resolve $kind/$imdb")
            } else {
                ctxRef.set(FmtCtx(kind, requestedS, requestedE, titleInfo.name, titleInfo.year))
                val want: Set<TvType> = if (kind == "movie")
                    setOf(TvType.Movie, TvType.AnimeMovie)
                else
                    setOf(TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA, TvType.AsianDrama, TvType.Documentary)
                val rank = cfg.order.withIndex().associate { it.value.lowercase() to it.index }
                val pairs = Repos.enabledProviders(cfg.providers).flatMap { (info, provs) ->
                    provs.filter { it.supportedTypes.intersect(want).isNotEmpty() }.map { info to it }
                }.sortedBy { rank[it.first.name.lowercase()] ?: 1000 }
                for ((info, prov) in pairs) {
                    val key = info.internalName + "#" + System.identityHashCode(prov)
                    retryDefs[key] = info.name to { scrapeProvider(prov, titleInfo, requestedS, requestedE) }
                    launchProvider(info.name, key) {
                        scrapeProvider(prov, titleInfo, requestedS, requestedE)
                    }
                }
            }

            // pass 1 done (this scope waited for all children). Retry ONLY providers
            // that FAILED (threw / timed out / null load) - providers that searched
            // fine and have no content are not retried; retrying all ~290 empties
            // hammered sites for nothing on every lookup.
            fanOutSummary(kind, id, "pass1", attempted, succeeded, noContent, failed, errorSamples)
            if (failed.isNotEmpty()) {
                com.lagradost.cloudstream3.network.CloudflareKiller.resetFailedHosts()
                AppLogger.i("Streams: retrying ${failed.size} failed providers")
                for (key in failed) {
                    retryDefs[key]?.let { (label, work) ->
                        launchProvider(label, key + ":r", work)
                    }
                }
            }
        }

        fanOutSummary(kind, id, "done", attempted, succeeded, noContent, failed, errorSamples)
    }

    /** One compact line per fan-out phase + the top distinct error shapes. Provider
     *  failures used to be swallowed silently, which made degraded nights undiagnosable. */
    private fun fanOutSummary(
        kind: String,
        id: String,
        phase: String,
        attempted: Set<String>,
        succeeded: Set<String>,
        noContent: Set<String>,
        failed: Set<String>,
        errorSamples: Map<String, String>,
    ) {
        AppLogger.i("Streams: fanout $phase $kind/$id: attempted=${attempted.size} ok=${succeeded.size} noContent=${noContent.size} failed=${failed.size}")
        if (errorSamples.isNotEmpty()) {
            errorSamples.values.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(5)
                .forEach { (err, n) -> AppLogger.i("Streams:   $n × $err") }
        }
    }
}
