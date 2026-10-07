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

class BridgeConfig(
    val providers: Set<String>,
    val catalogs: Boolean,
    val magnets: Boolean,
    val deadlineMs: Long? = null,
    val qualities: Set<Int> = setOf(2160, 1080, 720, 480, 360),
    val maxPerTier: Int = 0,
    val blockCam: Boolean = false,
    val fmt: String = "original",
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
                    root["fmt"]?.toString() ?: "original",
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
    private val scrapeScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

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

    /** Apply the formatter preset; returns (name line, title line). */
    private fun format(cfg: BridgeConfig, provider: String, link: ExtractorLink, q: String): Pair<String, String> = when {
        cfg.fmt == "modern" -> Pair(if (q.isBlank()) provider else "$provider\n$q", link.name)
        cfg.fmt == "minimal" -> Pair(if (q.isBlank()) "CSB" else "CSB $q", link.name)
        cfg.fmt.startsWith("custom:") -> {
            val parts = cfg.fmt.removePrefix("custom:").split("|", limit = 2)
            fun tpl(t: String) = t.replace("{provider}", provider).replace("{quality}", q).replace("{link}", link.name)
            Pair(tpl(parts.getOrElse(0) { "{provider} {quality}" }), tpl(parts.getOrElse(1) { "{link}" }))
        }
        else -> Pair(if (q.isBlank()) "CSB" else "CSB $q", "$provider • ${link.name}")
    }


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
        val (firstLine, titleLine) = format(cfg, providerName, link, q)
        val stream = linkedMapOf<String, Any?>(
            "name" to firstLine,
            "title" to titleLine,
            "description" to "$providerName • ${link.name}",
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


    private suspend fun scrapeProvider(
        prov: MainAPI,
        titleInfo: TitleInfo?,
        season: Int?,
        episode: Int?,
        matchUrl: String? = null,
    ): List<ExtractorLink> = withTimeout(Cfg.providerTimeoutMs) {
        val url = matchUrl ?: run {
            val results = runCatching {
                prov.search(titleInfo?.name ?: "", 1)?.items ?: prov.search(titleInfo?.name ?: "") ?: emptyList()
            }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
            results.firstOrNull { matches(it, titleInfo?.name ?: "", titleInfo?.year) }?.url
        } ?: return@withTimeout emptyList()
        val loaded = runCatching { prov.load(url) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            ?: return@withTimeout emptyList()
        runCatching { linksFromResponse(prov, loaded, season, episode) }
            .onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
    }


    private val rescraping = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Background re-scrape for a cached entry that was written before its scrape
     * finished ("incomplete"). Deduped per cache key; overwrites the cache with
     * the full merged list when done.
     */
    fun rescrapeAsync(cfg: BridgeConfig, kind: String, id: String, cacheKey: String) {
        if (rescraping.putIfAbsent(cacheKey, true) != null) return
        scrapeScope.launch {
            try {
                streamsForInternal(cfg, kind, id, cacheKey, respond = false)
            } catch (t: Throwable) {
                AppLogger.i("Streams: rescrape failed $kind/$id: ${t.message}")
            } finally {
                rescraping.remove(cacheKey)
            }
        }
    }

    /** Main entry: streams for a stremio-style request. Returns after the deadline; scraping continues detached. */
    fun streamsFor(cfg: BridgeConfig, kind: String, id: String, cacheKey: String? = null): List<Map<String, Any?>> =
        streamsForInternal(cfg, kind, id, cacheKey, respond = true)

    private fun streamsForInternal(cfg: BridgeConfig, kind: String, id: String, cacheKey: String?, respond: Boolean): List<Map<String, Any?>> {
        val t0 = System.currentTimeMillis()
        val deadline = cfg.deadlineMs ?: Cfg.deadlineMs
        val channel = Channel<Pair<String, List<ExtractorLink>>>(Channel.UNLIMITED)
        val collected = ArrayList<Pair<String, List<ExtractorLink>>>()

        val parent = scrapeScope.launch {
            fetchAll(cfg, kind, id, channel)
            channel.close()
        }

        runBlocking {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < deadline) {
                val remaining = deadline - (System.currentTimeMillis() - start)
                val got = withTimeoutOrNull(remaining) { channel.receive() } ?: break
                synchronized(collected) { collected.add(got) }
            }
        }

        val result = buildResult(cfg, collected)
        AppLogger.i("Streams: $kind/$id -> ${result.size} streams in ${System.currentTimeMillis() - t0} ms (scrape complete=${parent.isCompleted})")

        if (cacheKey != null) {
            // cache what we have now, marking whether stragglers are still running
            runCatching {
                Store.put(cacheKey, mapper.writeValueAsString(mapOf("streams" to result, "incomplete" to !parent.isCompleted)))
            }
        }

        // slow providers keep scraping in the background; when they finish, merge
        // their late results into the cache so the next tap shows the full list
        if (cacheKey != null) {
            scrapeScope.launch {
                runCatching {
                    val start = System.currentTimeMillis()
                    while (!parent.isCompleted && System.currentTimeMillis() - start < 600_000) {
                        val got = withTimeoutOrNull(30_000) { channel.receive() } ?: break
                        synchronized(collected) { collected.add(got) }
                    }
                    val full = buildResult(cfg, collected)
                    if (full.isNotEmpty() || respond) {
                        Store.put(cacheKey, mapper.writeValueAsString(mapOf("streams" to full, "incomplete" to false)))
                        AppLogger.i("Streams: $kind/$id late merge -> ${full.size} streams (cache updated)")
                    }
                }
            }
        }
        return result
    }

    private fun buildResult(cfg: BridgeConfig, collected: List<Pair<String, List<ExtractorLink>>>): List<Map<String, Any?>> {
        data class Row(val stream: Map<String, Any?>, val label: String, val link: ExtractorLink)
        val rank = cfg.order.withIndex().associate { it.value.lowercase() to it.index }
        var rows = ArrayList<Row>()
        val seen = HashSet<String>()
        for ((label, links) in collected) {
            for (link in links) {
                if (cfg.blockCam && isCam(link)) continue
                val tier = tierOf(link)
                if (tier != 0 && tier !in cfg.qualities) continue
                val st = toStremioStream(link, label, cfg) ?: continue
                if (seen.add(link.url)) rows.add(Row(st, label, link))
            }
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

    private suspend fun fetchAll(cfg: BridgeConfig, kind: String, id: String, channel: Channel<Pair<String, List<ExtractorLink>>>) {
        val sem = Semaphore(Cfg.maxConcurrent)

        fun launchProvider(label: String, work: suspend () -> List<ExtractorLink>) {
            scrapeScope.launch(Dispatchers.IO) {
                try {
                    sem.acquire()
                    try {
                        val res = work()
                        if (res.isNotEmpty()) channel.send(label to res)
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
                if (url != null) {
                    for ((info, provs) in Repos.enabledProviders(cfg.providers)) {
                        if (info.internalName != provName) continue
                        for (prov in provs) {
                            launchProvider(info.name) {
                                val loaded = runCatching { prov.load(url) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
                                linksFromResponse(prov, loaded, requestedS, requestedE)
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
                val want: Set<TvType> = if (kind == "movie")
                    setOf(TvType.Movie, TvType.AnimeMovie)
                else
                    setOf(TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA, TvType.AsianDrama, TvType.Documentary)
                val rank = cfg.order.withIndex().associate { it.value.lowercase() to it.index }
                val pairs = Repos.enabledProviders(cfg.providers).flatMap { (info, provs) ->
                    provs.filter { it.supportedTypes.intersect(want).isNotEmpty() }.map { info to it }
                }.sortedBy { rank[it.first.name.lowercase()] ?: 1000 }
                for ((info, prov) in pairs) {
                    launchProvider(info.name) {
                        scrapeProvider(prov, titleInfo, requestedS, requestedE)
                    }
                }
            }
        }
    }
}
