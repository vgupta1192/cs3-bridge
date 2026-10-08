package com.kissmissi.csbridge

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stream Master as one more source of this addon (2026-10-08).
 *
 * Stream Master (the stack's Node addon, container `stream-master`) runs ~25
 * JS providers, 13 of which have no CloudStream plugin at all (TopMovies,
 * EonMovies, Cinejoy, VidSpark, GokuHD, CineVood, LuxMovies, ...). Its own
 * catalog warmer keeps the top titles of every catalog row pre-scraped and
 * link-checked in its SQLite cache, so asking its /stream endpoint usually
 * answers in well under a second. Calling it here reuses that cache instead of
 * building a second one, and every link it returns was already checked as
 * playable. On a cache miss Stream Master scrapes and answers at its own early
 * exit (~10-15 s); the result is then cached on both sides.
 *
 * Shows in the configure page as a source (internal name [INTERNAL]) inside a
 * virtual "Stream Master (local)" repo, so it is switched on/off and ordered
 * like any plugin. CSBRIDGE_SM_URL="" disables it completely.
 */
object StreamMaster {
    const val INTERNAL = "StreamMaster"
    const val NAME = "Stream Master"
    const val REPO = "Stream Master (local)"
    const val REPO_URL = "local://stream-master"

    private val mapper = jacksonObjectMapper()
    val enabled get() = Cfg.smUrl.isNotBlank()

    private val client by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(Cfg.smTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(Cfg.smTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
    }

    @Volatile private var health = HealthEntry()

    fun healthEntry(): HealthEntry = health

    /** Manifest probe for the configure page's health views. */
    fun check(): HealthEntry {
        val t0 = System.currentTimeMillis()
        val ok = runCatching {
            client.newCall(okhttp3.Request.Builder().url("${Cfg.smUrl}/manifest.json").build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
        val now = System.currentTimeMillis()
        health = HealthEntry(if (ok) "up" else "down", if (ok) now else health.lastOk, now, now - t0)
        return health
    }

    private val sourceRegex = Regex("""Source:\s*([^•\n]+)""")
    private val sizeRegex = Regex("""(?i)\b\d+(?:[.,]\d+)?\s*(?:GB|GiB|MB|MiB)\b""")
    private val speedRegex = Regex("""\b(FAST|SLOW)\b""")
    private val junkRegex = Regex("""[^\p{L}\p{N}\p{P}\p{Zs}+]""")

    private fun quality(q: String?): Int = when {
        q == null -> 0
        q.contains("4k", true) || q.contains("2160") -> 2160
        q.contains("2k", true) || q.contains("1440") -> 1440
        q.contains("1080") -> 1080
        q.contains("720") -> 720
        q.contains("480") -> 480
        q.contains("360") -> 360
        else -> 0
    }

    private fun clean(s: String) = junkRegex.replace(s, " ").replace(Regex("\\s+"), " ").trim()

    /** Links for one stremio-style request (kind = movie|series, id = tt..[:s:e]). */
    suspend fun links(kind: String, id: String): List<ExtractorLink> = withContext(Dispatchers.IO) {
        val url = "${Cfg.smUrl}/stream/$kind/${java.net.URLEncoder.encode(id, "UTF-8").replace("%3A", ":")}.json"
        val body = client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Stream Master HTTP ${r.code}")
            r.body?.string() ?: ""
        }
        val streams: JsonNode = mapper.readTree(body).path("streams")
        val out = ArrayList<ExtractorLink>()
        for (s in streams) {
            val link = s.path("url").asText("")
            if (!link.startsWith("http")) continue
            val title = s.path("title").asText(s.path("description").asText(""))
            val name = s.path("name").asText("")
            val hints = s.path("behaviorHints")
            val source = sourceRegex.find(title)?.groupValues?.get(1)?.let(::clean)?.takeIf { it.isNotBlank() } ?: NAME
            val filename = hints.path("filename").asText("").ifBlank { clean(title.lineSequence().firstOrNull() ?: "") }
            // the link name carries what the filters/formatter parse: languages,
            // size, and Stream Master's FAST/SLOW speed verdict
            val extras = listOfNotNull(
                sizeRegex.find(title)?.value,
                speedRegex.find("$name $title")?.value,
            ) + title.lineSequence().filter { it.contains("🗣") }.map(::clean)
            val headers = HashMap<String, String>()
            hints.path("proxyHeaders").path("request").fields().forEach { (k, v) -> headers[k] = v.asText() }
            out.add(newExtractorLink(source, (listOf(filename) + extras).filter { it.isNotBlank() }.joinToString(" · "), link,
                if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                this.quality = quality(s.path("quality").asText(null) ?: name)
                this.headers = headers
            })
        }
        out
    }
}
