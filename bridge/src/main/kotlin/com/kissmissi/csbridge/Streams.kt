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
    // preferred stream languages in priority order (empty = no language
    // sorting/filtering); langFilter: 0 off, 1 drop non-matching but keep
    // unknown-language streams, 2 drop unknown too
    val langs: List<String> = emptyList(),
    val langFilter: Int = 0,
) {
    companion object {
        val CORE_REPOS = setOf("CNC Repo (All Language)", "Phisher Repo", "Megix Repo (Hindi & English)", "raghav repo")

        private fun boolFlag(v: Any?): Boolean = when (v) {
            is Boolean -> v
            is Number -> v.toInt() == 1
            else -> false
        }

        private fun fromMap(root: Map<*, *>): BridgeConfig {
            val p = (root["p"] as? Map<*, *>)?.filterValues { (it as? Number)?.toInt() != 0 }
                ?.keys?.map { it.toString() }?.toSet() ?: emptySet()
            val q = root["q"] as? Map<*, *>
            val qual = (q?.get("on") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet()
                ?: setOf(2160, 1080, 720, 480, 360)
            return BridgeConfig(
                p, boolFlag(root["c"]), boolFlag(root["m"]),
                (root["d"] as? Number)?.toLong(),
                qual,
                (q?.get("tier") as? Number)?.toInt() ?: 0,
                boolFlag(q?.get("cam")),
                FmtCfg.fromRaw(root["fmt"]),
                (root["order"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                (root["lg"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                (root["lgf"] as? Number)?.toInt() ?: 0,
            )
        }

        fun decode(s: String): BridgeConfig {
            // 1) server-side install record (the configure page POSTs it; once
            //    it exists the stored config IS the install and the URL never
            //    needs to change again)
            Installs.load(s)?.let { stored ->
                runCatching { return fromMap(jacksonObjectMapper().readValue<Map<String, Any>>(stored)) }
            }
            // 2) legacy: the config embedded in the URL segment itself
            return try {
                val fixed = s.replace('-', '+').replace('_', '/') + "=".repeat((4 - s.length % 4) % 4)
                val json = String(Base64.getDecoder().decode(fixed))
                fromMap(jacksonObjectMapper().readValue<Map<String, Any>>(json))
            } catch (e: Exception) {
                BridgeConfig(
                    Repos.plugins.values.filter { !Repos.isFailed(it) && it.repo in CORE_REPOS }.map { it.internalName }.toSet(),
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


    private val bracketRegex = Regex("""\[.*?\]|\(.*?\)""")
    private val nonAlnumRegex = Regex("[^a-z0-9]+")

    fun norm(s: String): String = s.lowercase()
        .replace(bracketRegex, " ")
        .replace(nonAlnumRegex, " ").trim()

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

    // languages recognised in link text for the language filter/sort — same
    // names the formatter glue uses so cfg.langs matches {stream.languages}
    private val LANG_NAMES = listOf(
        "Hindi", "English", "Tamil", "Telugu", "Malayalam", "Kannada", "Bengali", "Punjabi",
        "Marathi", "Japanese", "Korean", "Chinese", "Spanish", "French", "German", "Turkish",
        "Arabic", "Portuguese", "Russian", "Italian", "Indonesian", "Thai", "Vietnamese", "Urdu",
    )
    private val LANG_REGEX = LANG_NAMES.map { it to Regex("\\b${it}\\b", RegexOption.IGNORE_CASE) }
    private val dubRegex = Regex("""\bdubs?\b|\bdubbed\b""", RegexOption.IGNORE_CASE)
    private val LANG_BY_CODE = mapOf(
        "hi" to "Hindi", "en" to "English", "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam",
        "kn" to "Kannada", "bn" to "Bengali", "pa" to "Punjabi", "mr" to "Marathi", "ja" to "Japanese",
        "ko" to "Korean", "zh" to "Chinese", "es" to "Spanish", "fr" to "French", "de" to "German",
        "tr" to "Turkish", "ar" to "Arabic", "pt" to "Portuguese", "ru" to "Russian", "it" to "Italian",
        "id" to "Indonesian", "th" to "Thai", "vi" to "Vietnamese", "ur" to "Urdu",
    )

    /** Languages of a link for filtering/sorting: explicit tokens in the link
     *  text win, then a dub track on a foreign provider = English, then the
     *  provider's primary language (mirrors csb-glue langOf()). */
    private fun linkLanguages(link: ExtractorLink, providerLang: String?): List<String> {
        val filename = runCatching {
            val f = java.net.URL(link.url).file.substringBefore('?').substringBefore('#')
            java.net.URLDecoder.decode(f.substringAfterLast('/'), "UTF-8")
        }.getOrNull() ?: ""
        val text = "${link.name} ${link.source} $filename"
        val parsed = LANG_REGEX.filter { it.second.containsMatchIn(text) }.map { it.first }
        if (parsed.isNotEmpty()) return parsed
        if (dubRegex.containsMatchIn(text)) return listOf("English")
        val code = providerLang?.substringBefore('-')?.lowercase() ?: ""
        return LANG_BY_CODE[code]?.let { listOf(it) } ?: emptyList()
    }

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

    private fun metaJson(link: ExtractorLink, provider: String, lang: String? = null): String = mapper.writeValueAsString(
        linkedMapOf<String, Any?>(
            "provider" to provider,
            "linkName" to link.name,
            "source" to link.source,
            "quality" to link.quality,
            "url" to link.url,
            "kind" to link.type.name,
            // provider's primary language (repo metadata / plugin lang) — the
            // formatter glue fills {stream.languages} with it when the link
            // text itself carries no language tokens
            "lang" to lang,
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
        timeoutMs: Long = Cfg.providerTimeoutMs,
    ): ProviderOutcome = withTimeout(timeoutMs) {
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

    /** cacheKey -> answer of the request currently scraping it. Apps fire the same
     *  stream request 2-3x (retries, prefetch, two devices); each used to start its
     *  own full fan-out over every provider. Followers now wait for the leader. */
    private val inflight = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<List<Map<String, Any?>>>>()

    // ---- per-provider circuit breaker ----
    // A provider whose site is dead / blocking us failed every request, burning
    // a search + load + retry each time. After BREAKER_FAILS consecutive failures
    // it is skipped for BREAKER_OPEN_MS, then gets one probe request again.
    private class Breaker { var fails = 0; var openUntil = 0L }
    private val breakers = java.util.concurrent.ConcurrentHashMap<String, Breaker>()
    private val BREAKER_FAILS = System.getenv("CSBRIDGE_BREAKER_FAILS")?.toIntOrNull() ?: 3
    private val BREAKER_OPEN_MS = System.getenv("CSBRIDGE_BREAKER_OPEN_MS")?.toLongOrNull() ?: (15L * 60 * 1000)

    private fun breakerOpen(name: String): Boolean = (breakers[name]?.openUntil ?: 0L) > System.currentTimeMillis()

    private fun breakerResult(name: String, ok: Boolean) {
        val b = breakers.computeIfAbsent(name) { Breaker() }
        synchronized(b) {
            if (ok) { b.fails = 0; b.openUntil = 0L; return }
            b.fails++
            if (b.fails >= BREAKER_FAILS) {
                b.openUntil = System.currentTimeMillis() + BREAKER_OPEN_MS
                // half-open: one more failure after the pause re-opens at once
                b.fails = BREAKER_FAILS - 1
                AppLogger.i("Streams: breaker open for $name (${BREAKER_OPEN_MS / 60000} min) after repeated failures")
            }
        }
    }

    /** Snapshot for /api/breakers. */
    fun breakerState(): Map<String, Any?> = breakers.entries.filter { it.value.openUntil > System.currentTimeMillis() }
        .associate { it.key to mapOf("openForS" to (it.value.openUntil - System.currentTimeMillis()) / 1000) }

    // ---- playable-link check (background only, never on the response path) ----
    // Before the full list is cached, links are probed with a 1-byte ranged GET;
    // definitively dead ones (404/410/451, unknown host, connection refused)
    // are dropped so repeat taps only show links that open. Timeouts are kept.
    private val validateOn = System.getenv("CSBRIDGE_VALIDATE") != "0"
    private val validateMax = System.getenv("CSBRIDGE_VALIDATE_MAX")?.toIntOrNull() ?: 60
    private val probeVerdicts = object : java.util.LinkedHashMap<String, Pair<Boolean, Long>>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Boolean, Long>>?): Boolean = size > 4000
    }
    private const val PROBE_TTL = 30L * 60 * 1000
    private val probeClient by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** true = definitively dead. */
    private fun probeDead(url: String, headers: Map<String, String>): Boolean {
        synchronized(probeVerdicts) {
            probeVerdicts[url]?.let { (dead, ts) -> if (System.currentTimeMillis() - ts < PROBE_TTL) return dead }
        }
        val dead = try {
            val b = okhttp3.Request.Builder().url(url).header("Range", "bytes=0-0")
            headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
            if (headers.keys.none { it.equals("user-agent", true) }) b.header("user-agent", com.lagradost.cloudstream3.USER_AGENT)
            // 401/403 are often UA/referer/IP-bound CDN answers that still play in
            // the app's player — only "gone" answers count as dead
            probeClient.newCall(b.build()).execute().use { r -> r.code == 404 || r.code == 410 || r.code == 451 }
        } catch (e: java.net.UnknownHostException) { true
        } catch (e: java.net.ConnectException) { true
        } catch (e: Exception) { false }
        synchronized(probeVerdicts) { probeVerdicts[url] = dead to System.currentTimeMillis() }
        return dead
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun dropDead(streams: List<Map<String, Any?>>): List<Map<String, Any?>> {
        if (!validateOn || streams.isEmpty()) return streams
        val sem = Semaphore(8)
        val dead = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
        kotlinx.coroutines.coroutineScope {
            streams.take(validateMax).forEachIndexed { i, st ->
                val url = st["url"] as? String ?: return@forEachIndexed
                if (!url.startsWith("http")) return@forEachIndexed
                val headers = (((st["behaviorHints"] as? Map<String, Any?>)?.get("proxyHeaders") as? Map<String, Any?>)
                    ?.get("request") as? Map<String, String>) ?: emptyMap()
                launch(Dispatchers.IO) {
                    sem.acquire()
                    try { if (probeDead(url, headers)) dead.add(i) } finally { sem.release() }
                }
            }
        }
        if (dead.isEmpty()) return streams
        AppLogger.i("Streams: dropped ${dead.size} dead links of ${streams.size}")
        return streams.filterIndexed { i, _ -> i !in dead }
    }

    /**
     * Background re-scrape: for a cache entry written before its scrape finished
     * ("incomplete"), or a degraded low-stream entry old enough to be worth
     * refreshing (see [maybeRescrapeDegraded]). Deduped per cache key and against
     * a still-running scrape for the same key; overwrites the cache with the full
     * merged list when done.
     */
    fun rescrapeAsync(cfg: BridgeConfig, kind: String, id: String, cacheKey: String, entry: Map<String, Any?>? = null) {
        // an "incomplete" entry gets one rescrape per INCOMPLETE_RESCRAPE_MS, not one per tap
        val ts = (entry?.get("ts") as? Number)?.toLong() ?: 0L
        if (ts > 0L && System.currentTimeMillis() - ts < INCOMPLETE_RESCRAPE_MS) return
        // one background refresh per key per INCOMPLETE_RESCRAPE_MS, even if it wrote nothing
        val now = System.currentTimeMillis()
        val last = lastBackground.put(cacheKey, now)
        if (last != null && now - last < INCOMPLETE_RESCRAPE_MS) { lastBackground[cacheKey] = last; return }
        if (lastBackground.size > 20000) lastBackground.clear()
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
    private const val INCOMPLETE_RESCRAPE_MS = 5L * 60 * 1000

    private val lastBackground = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Process-wide provider-scrape caps: live requests (priority lane included) and the warmer. */
    private val liveGlobal = Semaphore(Cfg.globalMaxConcurrent)
    private val warmGlobal = Semaphore(Cfg.warmGlobalMaxConcurrent)

    /** Live fan-outs currently running (first answer + their background tail). */
    private val liveFanouts = java.util.concurrent.atomic.AtomicInteger(0)
    fun liveBusy() = liveFanouts.get() > 0

    /** cacheKey -> background job that writes the full list (late merge). */
    private val lateJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    /** The user's top-ranked providers (configure-page order), lowercased labels. */
    private fun priorityLabels(cfg: BridgeConfig): Set<String> =
        cfg.order.take(Cfg.priorityCount).map { it.lowercase() }.toSet()

    fun isScraping(cacheKey: String) = activeScrapes.containsKey(cacheKey) || lateJobs.containsKey(cacheKey)

    /** Warmer entry: full scrape into the cache, returns when the full list is written. */
    suspend fun warm(cfg: BridgeConfig, kind: String, id: String, cacheKey: String) {
        if (isScraping(cacheKey) || activeScrapes.putIfAbsent(cacheKey, true) != null) return
        try {
            kotlinx.coroutines.withContext(Dispatchers.IO) { streamsForInternal(cfg, kind, id, cacheKey, respond = false, warm = true) }
            lateJobs[cacheKey]?.join()
        } finally {
            activeScrapes.remove(cacheKey)
        }
    }

    fun streamsFor(cfg: BridgeConfig, kind: String, id: String, cacheKey: String? = null): List<Map<String, Any?>> {
        if (cacheKey == null) return streamsForInternal(cfg, kind, id, null, respond = true)
        val mine = java.util.concurrent.CompletableFuture<List<Map<String, Any?>>>()
        val leader = inflight.putIfAbsent(cacheKey, mine)
        if (leader != null) {
            // the leader answers at its deadline plus formatting time; wait well past it
            val wait = (cfg.deadlineMs ?: Cfg.deadlineMs) + 20_000
            AppLogger.i("Streams: $kind/$id joining in-flight scrape")
            return runCatching { leader.get(wait, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrElse {
                // leader still busy: whatever it has cached so far beats nothing
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    (mapper.readValue<Map<String, Any?>>(Store.get(cacheKey, 6L * 3600 * 1000) ?: "{}")["streams"] as? List<Map<String, Any?>>)
                }.getOrNull() ?: emptyList()
            }
        }
        try {
            val r = streamsForInternal(cfg, kind, id, cacheKey, respond = true)
            mine.complete(r)
            return r
        } catch (t: Throwable) {
            mine.complete(emptyList())
            throw t
        } finally {
            inflight.remove(cacheKey, mine)
        }
    }

    private fun streamsForInternal(cfg: BridgeConfig, kind: String, id: String, cacheKey: String?, respond: Boolean, warm: Boolean = false): List<Map<String, Any?>> {
        val t0 = System.currentTimeMillis()
        // stop collecting a little before the configured deadline: formatting
        // the answer still has to fit before the app gives up on the request
        val deadline = ((cfg.deadlineMs ?: Cfg.deadlineMs) - Cfg.renderHeadroomMs).coerceAtLeast(5000L)
        val priority = priorityLabels(cfg)
        val channel = Channel<Triple<String, String?, List<ExtractorLink>>>(Channel.UNLIMITED)
        val collected = ArrayList<Triple<String, String?, List<ExtractorLink>>>()
        val ctxRef = java.util.concurrent.atomic.AtomicReference<FmtCtx?>(null)
        // resolver failure (no fan-out at all) must not poison the cache with a
        // "complete" empty entry — marked by fetchAll, checked before caching
        val noAttempt = java.util.concurrent.atomic.AtomicBoolean(false)

        if (cacheKey != null) activeScrapes[cacheKey] = true
        if (!warm) liveFanouts.incrementAndGet()
        val parent = scrapeScope.launch {
            fetchAll(cfg, kind, id, channel, ctxRef, noAttempt, warm, priority)
            channel.close()
        }
        parent.invokeOnCompletion {
            if (cacheKey != null) activeScrapes.remove(cacheKey)
            if (!warm) liveFanouts.decrementAndGet()
        }

        runBlocking {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < deadline) {
                val remaining = deadline - (System.currentTimeMillis() - start)
                val r = withTimeoutOrNull(remaining) { channel.receiveCatching() } ?: break
                val got = r.getOrNull() ?: break
                synchronized(collected) { collected.add(got) }
                // fast-first: a useful partial beats the full wait — apps time out
                // around 15-30 s, so waiting the whole deadline served empty pages.
                // Both gates count: enough links AND enough distinct providers
                // (one chatty fast provider must not own the whole first page)
                val elapsed = System.currentTimeMillis() - start
                // priority lane: once enough of the user's top-ranked providers
                // have answered with links, serve straight away
                if (respond && priority.isNotEmpty() && elapsed >= Cfg.priorityMinMs) {
                    // count only links that survive this install's filters
                    // (torrents off, quality tiers, cam, language) — raw link
                    // counts sent early answers that filtered down to 0 streams
                    val (n, pdone) = synchronized(collected) {
                        val useful = collected.filter { usable(cfg, it.third, it.second) > 0 }
                        useful.sumOf { usable(cfg, it.third, it.second) } to useful.map { it.first.lowercase() }.filter { it in priority }.distinct().size
                    }
                    if (pdone >= Cfg.priorityMinProviders && n >= Cfg.priorityMinStreams) break
                }
                if (elapsed >= Cfg.fastWindowMs) {
                    val (n, provs) = synchronized(collected) {
                        val useful = collected.filter { usable(cfg, it.third, it.second) > 0 }
                        useful.sumOf { usable(cfg, it.third, it.second) } to useful.map { it.first }.distinct().size
                    }
                    if (n >= Cfg.fastMinStreams && provs >= Cfg.fastMinProviders) break
                }
            }
        }

        if (noAttempt.get() && collected.isEmpty()) {
            AppLogger.i("Streams: $kind/$id -> resolver failed after ${System.currentTimeMillis() - t0} ms, serving empty uncached")
            return emptyList()
        }

        val tb = System.currentTimeMillis()
        val result = buildResult(cfg, collected, ctxRef.get(), bg = !respond)
        val buildMs = System.currentTimeMillis() - tb
        if (buildMs > 1500) AppLogger.i("Streams: $kind/$id formatting took $buildMs ms")
        AppLogger.i("Streams: $kind/$id -> ${result.size} streams in ${System.currentTimeMillis() - t0} ms (scrape complete=${parent.isCompleted})")

        if (cacheKey != null && respond) {
            // cache what we have now, marking whether stragglers are still running.
            // Background (re)scrapes and the warmer only write the full list, so a
            // partial never replaces a good older entry.
            runCatching {
                Store.put(cacheKey, mapper.writeValueAsString(mapOf("streams" to result, "incomplete" to !parent.isCompleted, "ts" to System.currentTimeMillis())))
            }
        }

        // slow providers keep scraping in the background; when they finish, merge
        // their late results into the cache so the next tap shows the full list
        if (cacheKey != null) {
            lateJobs[cacheKey] = scrapeScope.launch {
                runCatching {
                    // drain until the fan-out closes the channel (its phases are
                    // wall-clock capped). The old 30 s-lull exit left entries
                    // flagged incomplete, which re-ran the whole fan-out on the
                    // next tap.
                    val start = System.currentTimeMillis()
                    while (System.currentTimeMillis() - start < 600_000) {
                        val left = 600_000 - (System.currentTimeMillis() - start)
                        val r = withTimeoutOrNull(left) { channel.receiveCatching() } ?: break
                        val got = r.getOrNull() ?: break
                        synchronized(collected) { collected.add(got) }
                    }
                    withTimeoutOrNull(5_000) { parent.join() }
                    val full = dropDead(buildResult(cfg, collected, ctxRef.get(), bg = true))
                    if (full.isNotEmpty() || respond) {
                        // a 30s lull can break the loop while the parent scrape is
                        // still running - keep the incomplete flag honest so the
                        // next request can still trigger one deduped rescrape
                        Store.put(cacheKey, mapper.writeValueAsString(mapOf("streams" to full, "incomplete" to !parent.isCompleted, "ts" to System.currentTimeMillis())))
                        AppLogger.i("Streams: $kind/$id ${if (warm) "warm" else "late merge"} -> ${full.size} streams (cache updated, scrape complete=${parent.isCompleted})")
                    }
                }
                lateJobs.remove(cacheKey)
            }
        }
        return result
    }

    private val ANIME_TYPES = setOf("anime", "animemovie", "ova", "cartoon")
    private val animeName = Regex("(?i)anime|donghua|hianime|^ani[a-z]")

    /** Plugin serves only animation (repo tvTypes), or is an anime site by name. */
    private fun animeOnly(info: PluginInfo): Boolean {
        val t = info.tvTypes.map { it.lowercase() }
        return (t.isNotEmpty() && t.all { it in ANIME_TYPES }) || animeName.containsMatchIn(info.internalName)
    }

    /** How many of [links] this install would actually show (mirrors buildResult's filters). */
    private fun usable(cfg: BridgeConfig, links: List<ExtractorLink>, lang: String?): Int = links.count { link ->
        if (link.url.isBlank()) return@count false
        if ((link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET) && !cfg.magnets) return@count false
        if (cfg.blockCam && isCam(link)) return@count false
        val tier = tierOf(link)
        if (tier != 0 && tier !in cfg.qualities) return@count false
        if (cfg.langs.isNotEmpty() && cfg.langFilter > 0) {
            val ls = linkLanguages(link, lang)
            if (ls.isEmpty()) cfg.langFilter == 1 else ls.any { cfg.langs.contains(it) }
        } else true
    }

    private fun buildResult(cfg: BridgeConfig, collected: List<Triple<String, String?, List<ExtractorLink>>>, ctx: FmtCtx?, bg: Boolean = false): List<Map<String, Any?>> {
        data class Row(val stream: Map<String, Any?>, val label: String, val lang: String?, val link: ExtractorLink)
        // one templates lookup per serve; null = built-in naming everywhere
        val templates = runCatching { Formatter.templates(cfg.fmt.f, cfg.fmt.n, cfg.fmt.d, bg) }.getOrNull()
        val rank = cfg.order.withIndex().associate { it.value.lowercase() to it.index }
        data class Cand(val label: String, val lang: String?, val link: ExtractorLink)
        val candidates = ArrayList<Cand>()
        val seen = HashSet<String>()
        for ((label, lang, links) in collected) {
            for (link in links) {
                if (cfg.blockCam && isCam(link)) continue
                val tier = tierOf(link)
                if (tier != 0 && tier !in cfg.qualities) continue
                if (seen.add(link.url)) candidates.add(Cand(label, lang, link))
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
                    items.add(metaJson(cand.link, cand.label, cand.lang) to ctxJson(ctx))
                }
            }
            if (items.isNotEmpty()) {
                val rendered = runCatching { Formatter.renderBatch(templates, items, bg) }.getOrElse { emptyList() }
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
            rows.add(Row(st, cand.label, cand.lang, cand.link))
        }
        rows.sortBy { rank[it.label.lowercase()] ?: 1000 }
        if (cfg.langs.isNotEmpty()) {
            // stable sort: preferred languages first, provider order kept within
            // the same language rank; unknown-language streams rank last
            rows = ArrayList(rows.sortedBy { row ->
                linkLanguages(row.link, row.lang).minOfOrNull { l -> cfg.langs.indexOf(l).let { if (it < 0) Int.MAX_VALUE else it } } ?: Int.MAX_VALUE
            })
            if (cfg.langFilter > 0) {
                rows = ArrayList(rows.filter { row ->
                    val ls = linkLanguages(row.link, row.lang)
                    if (ls.isEmpty()) cfg.langFilter == 1 else ls.any { cfg.langs.contains(it) }
                })
            }
        }
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
        channel: Channel<Triple<String, String?, List<ExtractorLink>>>,
        ctxRef: java.util.concurrent.atomic.AtomicReference<FmtCtx?>,
        noAttempt: java.util.concurrent.atomic.AtomicBoolean,
        warm: Boolean = false,
        priority: Set<String> = emptySet(),
    ) {
        // the warmer gets a smaller lane and shorter provider timeout so it never
        // crowds out a live request
        val sem = Semaphore(if (warm) Cfg.warmMaxConcurrent else Cfg.maxConcurrent)
        val provTimeout = if (warm) Cfg.warmProviderTimeoutMs else Cfg.providerTimeoutMs
        val attempted = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val succeeded = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val noContent = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val errorSamples = java.util.concurrent.ConcurrentHashMap<String, String>()
        val retryDefs = java.util.concurrent.ConcurrentHashMap<String, Triple<String, String?, suspend () -> ProviderOutcome>>()
        val okTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
        // only transient failures (timeouts, network errors) are worth a second wave
        val retryable = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        fun launchProvider(scope: kotlinx.coroutines.CoroutineScope, label: String, key: String, lang: String?, work: suspend () -> ProviderOutcome) {
            attempted.add(key)
            scope.launch(scrapeDispatcher) {
                try {
                    // priority lane: the user's top providers start at once
                    // instead of queueing behind the semaphore
                    val lane = !warm && (label.lowercase() in priority || key.substringBefore('#').lowercase() in priority)
                    if (!lane) sem.acquire()
                    // process-wide cap shared by every request (and a small
                    // separate one for the warmer): bursts of several titles
                    // used to run 70-100 provider scrapes at once (1.8 GiB peaks)
                    // the warmer yields completely while any live fan-out runs:
                    // no new warm provider scrape starts until it is done
                    if (warm) while (liveFanouts.get() > 0) kotlinx.coroutines.delay(1000)
                    val global = if (warm) warmGlobal else liveGlobal
                    try { global.acquire() } catch (t: Throwable) { if (!lane) sem.release(); throw t }
                    try {
                        val tStart = System.currentTimeMillis()
                        val res = try {
                            work()
                        } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
                            ProviderOutcome(emptyList(), t)
                        } catch (t: CancellationException) {
                            throw t
                        } catch (t: Throwable) {
                            ProviderOutcome(emptyList(), t)
                        }
                        val bname = key.removeSuffix(":r")
                        if (res.links.isNotEmpty()) {
                            succeeded.add(key)
                            okTimes.merge(label, System.currentTimeMillis() - tStart) { a, b -> minOf(a, b) }
                            breakerResult(bname, true)
                            channel.send(Triple(label, lang, res.links))
                        } else if (res.error != null) {
                            failed.add(key)
                            val e = res.error
                            val transient = e is kotlinx.coroutines.TimeoutCancellationException || e is java.io.IOException ||
                                e.cause is java.io.IOException
                            if (transient) retryable.add(key)
                            // only network-level failures (site down / unreachable /
                            // timing out) trip the breaker: many plugins THROW when a
                            // title is simply missing, and counting those paused
                            // healthy CNC Verse sources after one series lookup.
                            // The retry wave must not double-count one request.
                            if (transient && !key.endsWith(":r")) breakerResult(bname, false)
                            errorSamples.putIfAbsent(label, "${res.error::class.java.simpleName}: ${res.error.message?.take(120) ?: "no message"}")
                        } else {
                            noContent.add(key)
                            breakerResult(bname, true)
                        }
                    } finally {
                        global.release()
                        if (!lane) sem.release()
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
                    fanOutPhase(Cfg.providerTimeoutMs + 30_000, "$kind/$id", "pass1-csb") {
                        kotlinx.coroutines.coroutineScope {
                            for ((info, provs) in Repos.enabledProviders(cfg.providers)) {
                                if (info.internalName != provName) continue
                                for (prov in provs) {
                                    val plang = info.language ?: prov.lang
                                    launchProvider(this, info.name, info.internalName + "#" + System.identityHashCode(prov), plang) {
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
                }
            }
        } else {
            val imdb = id.substringBefore(":")
            val requestedS = id.split(":").getOrNull(1)?.toIntOrNull()
            val requestedE = id.split(":").getOrNull(2)?.toIntOrNull()
            val titleInfo = Resolver.resolve(kind, imdb)
            if (titleInfo == null) {
                noAttempt.set(true)
                AppLogger.i("Streams: could not resolve $kind/$imdb")
            } else {
                ctxRef.set(FmtCtx(kind, requestedS, requestedE, titleInfo.name, titleInfo.year))
                val want: Set<TvType> = if (kind == "movie")
                    setOf(TvType.Movie, TvType.AnimeMovie)
                else
                    setOf(TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA, TvType.AsianDrama, TvType.Documentary)
                val rank = cfg.order.withIndex().associate { it.value.lowercase() to it.index }
                val allPairs = Repos.enabledProviders(cfg.providers).flatMap { (info, provs) ->
                    provs.filter { it.supportedTypes.intersect(want).isNotEmpty() }.map { info to it }
                }.sortedBy { rank[it.first.name.lowercase()] ?: 1000 }
                // anime/cartoon-only sources searched every live-action title and
                // never matched — skip them unless the title is animated (or unknown)
                val typed = if (titleInfo.animated == false) allPairs.filter { !animeOnly(it.first) } else allPairs
                if (typed.size < allPairs.size) AppLogger.i("Streams: $kind/$id skipping ${allPairs.size - typed.size} anime-only providers (live-action title)")
                val pairs = typed.filter { !breakerOpen(it.first.internalName + "#" + System.identityHashCode(it.second)) }
                if (pairs.size < allPairs.size) AppLogger.i("Streams: $kind/$id skipping ${allPairs.size - pairs.size} providers with an open breaker")
                fanOutPhase(Cfg.providerTimeoutMs + 30_000, "$kind/$id", "pass1") {
                    kotlinx.coroutines.coroutineScope {
                        for ((info, prov) in pairs) {
                            val key = info.internalName + "#" + System.identityHashCode(prov)
                            val plang = info.language ?: prov.lang
                            retryDefs[key] = Triple(info.name, plang) { scrapeProvider(prov, titleInfo, requestedS, requestedE, timeoutMs = provTimeout) }
                            launchProvider(this, info.name, key, plang) {
                                scrapeProvider(prov, titleInfo, requestedS, requestedE, timeoutMs = provTimeout)
                            }
                        }
                    }
                }
            }

            // pass 1 REALLY is done here (the phase scope above waited for every
            // child, bounded by the phase cap for providers that block in
            // non-cooperative code a cooperative withTimeout cannot interrupt).
            // The original structure put this code inside the coroutineScope body,
            // which runs BEFORE the wait - the retry wave fired at t=0 with nothing
            // succeeded yet, so every fan-out launched ~2x providers at once and
            // hammered the sites into rate-limiting us.
            // Retry ONLY providers that FAILED (threw / timed out / null load) -
            // providers that searched fine and have no content are not retried.
            fanOutSummary(kind, id, "pass1", attempted, succeeded, noContent, failed, errorSamples)
            val toRetry = if (warm) emptyList() else failed.filter { it in retryable && !breakerOpen(it) }
            if (toRetry.isNotEmpty()) {
                AppLogger.i("Streams: retrying ${toRetry.size} of ${failed.size} failed providers (transient errors only)")
                fanOutPhase(Cfg.providerTimeoutMs + 30_000, "$kind/$id", "retry") {
                    kotlinx.coroutines.coroutineScope {
                        for (key in toRetry) {
                            retryDefs[key]?.let { (label, lang, work) ->
                                launchProvider(this, label, key + ":r", lang, work)
                            }
                        }
                    }
                }
            }
        }

        fanOutSummary(kind, id, "done", attempted, succeeded, noContent, failed, errorSamples)
        if (okTimes.isNotEmpty()) AppLogger.i("Streams:   ok in: " + okTimes.entries.sortedBy { it.value }
            .joinToString(", ") { "${it.key} ${it.value / 1000}s" })
    }

    /** Run one fan-out phase (a coroutineScope whose children are provider scrapes)
     *  with a hard wall-clock cap. Some providers block in non-cooperative code
     *  (raw okhttp execute / DNS lookups) that a cooperative withTimeout cannot
     *  interrupt - without the cap one stuck child stalls the retry wave, the
     *  done summary and channel.close() indefinitely. On cap: the phase's job is
     *  cancelled and we move on; a thread still blocked in a provider dies at its
     *  next suspension point. */
    private suspend fun fanOutPhase(capMs: Long, label: String, phase: String, block: suspend () -> Unit) {
        try {
            kotlinx.coroutines.withTimeout(capMs) {
                block()
            }
        } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
            AppLogger.i("Streams: phase $phase $label hit the ${(capMs / 1000)}s cap (stuck provider?), continuing")
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            AppLogger.i("Streams: phase $phase $label failed: ${t.message}")
        }
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
