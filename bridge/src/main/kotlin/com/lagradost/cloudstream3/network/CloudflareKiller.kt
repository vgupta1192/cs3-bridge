package com.lagradost.cloudstream3.network

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.common.logging.AppLogger
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Cloudflare challenge handling for the headless bridge.
 *
 * There is no WebView/Playwright here (WebViewResolver.jvm is a stub), so a
 * challenge is solved by trawl, the stack's FlareSolverr-compatible camoufox
 * service, the same way Stream Master's kisskh provider does it. One solve
 * yields a cf_clearance cookie bound to trawl's User-Agent and this host's IP;
 * requests to that host then carry the cookie + UA until it expires.
 *
 * Solves only run for hosts on CSBRIDGE_CF_SOLVE_HOSTS (comma list, suffix
 * match; default the kisskh domains) and when CSBRIDGE_TRAWL_URL is set, so a
 * fan-out over dozens of challenged sites cannot queue up browser solves.
 * Every other challenged host gets the challenge page passed through untouched.
 */
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val ERROR_CODES = listOf(403, 503)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")
        private val mapper = jacksonObjectMapper()

        private val trawlUrl: String? = System.getenv("CSBRIDGE_TRAWL_URL")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        private val solveHosts: List<String> = (System.getenv("CSBRIDGE_CF_SOLVE_HOSTS") ?: "kisskh.is,kisskh.id,kisskh.co,kisskh.nl")
            .split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        private const val FAIL_RETRY_MS = 30L * 60 * 1000
        private const val CLEARANCE_MAX_MS = 6L * 3600 * 1000

        // shared by every CloudflareKiller instance (plugins create their own)
        private val cookies = ConcurrentHashMap<String, Map<String, String>>()
        private val agents = ConcurrentHashMap<String, String>()
        private val until = ConcurrentHashMap<String, Long>()
        private val failedAt = ConcurrentHashMap<String, Long>()
        private val announced = ConcurrentHashMap.newKeySet<String>()
        private val hostLocks = ConcurrentHashMap<String, Any>()

        private val solverClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .callTimeout(95, TimeUnit.SECONDS)
                .build()
        }

        private val storeFile: File? get() = System.getenv("CSBRIDGE_DATA_DIR")?.let { File(it, "cf-clearance.json") }

        data class Saved(val cookies: Map<String, String> = emptyMap(), val ua: String = "", val until: Long = 0)

        init {
            runCatching {
                storeFile?.takeIf { it.exists() }?.let { f ->
                    mapper.readValue<Map<String, Saved>>(f).forEach { (h, s) ->
                        if (s.until > System.currentTimeMillis() && s.cookies.isNotEmpty()) {
                            cookies[h] = s.cookies; agents[h] = s.ua; until[h] = s.until
                        }
                    }
                }
            }
        }

        private fun persist() {
            runCatching {
                val f = storeFile ?: return
                val snap = cookies.keys.associateWith { h -> Saved(cookies[h] ?: emptyMap(), agents[h] ?: "", until[h] ?: 0) }
                f.writeText(mapper.writeValueAsString(snap))
            }
        }

        fun canSolve(host: String): Boolean {
            if (trawlUrl == null) return false
            val h = host.lowercase()
            return solveHosts.any { h == it || h.endsWith(".$it") }
        }

        /** Kept for callers; failed hosts are retried after FAIL_RETRY_MS anyway. */
        fun resetFailedHosts() {
            failedAt.clear()
        }

        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";")
                .mapNotNull { pair ->
                    val split = pair.split("=", limit = 2)
                    val key = split.getOrNull(0)?.trim().orEmpty()
                    val value = split.getOrNull(1)?.trim().orEmpty()
                    if (key.isNotEmpty() && value.isNotEmpty()) key to value else null
                }
                .toMap()
        }

        private fun isChallenge(r: Response): Boolean =
            r.code in ERROR_CODES &&
                CLOUDFLARE_SERVERS.any { (r.header("Server") ?: "").contains(it, ignoreCase = true) } &&
                (r.header("Content-Type") ?: "").contains("text/html", ignoreCase = true)

        private fun valid(host: String) = cookies.containsKey(host) && (until[host] ?: 0L) > System.currentTimeMillis()

        /** Solve [host] through trawl; true when a cf_clearance is stored. One solve per host at a time. */
        private fun solve(host: String, scheme: String): Boolean {
            val lock = hostLocks.computeIfAbsent(host) { Any() }
            synchronized(lock) {
                if (valid(host)) return true // another request just solved it
                failedAt[host]?.let { if (System.currentTimeMillis() - it < FAIL_RETRY_MS) return false }
                val t0 = System.currentTimeMillis()
                return try {
                    val body = mapper.writeValueAsString(mapOf("cmd" to "request.get", "url" to "$scheme://$host/", "maxTimeout" to 60000))
                    val req = Request.Builder().url("$trawlUrl/v1").post(body.toRequestBody("application/json".toMediaType())).build()
                    val root = solverClient.newCall(req).execute().use { mapper.readTree(it.body?.string() ?: "{}") }
                    val sol = root.path("solution")
                    // trawl's browser is shared: keep only this host's cookies
                    val jar = sol.path("cookies").filter { c ->
                        val d = c.path("domain").asText("").removePrefix(".").lowercase()
                        d.isNotEmpty() && (host == d || host.endsWith(".$d"))
                    }
                    val cf = jar.firstOrNull { it.path("name").asText() == "cf_clearance" }
                    if (root.path("status").asText() != "ok" || cf == null) throw IllegalStateException("no cf_clearance (status ${root.path("status").asText("?")})")
                    val exp = cf.path("expires").asDouble(0.0).let { if (it > 0) (it * 1000).toLong() else System.currentTimeMillis() + 30 * 60 * 1000 }
                    cookies[host] = jar.associate { it.path("name").asText() to it.path("value").asText() }
                    agents[host] = sol.path("userAgent").asText("")
                    until[host] = minOf(exp - 60_000, System.currentTimeMillis() + CLEARANCE_MAX_MS)
                    failedAt.remove(host)
                    persist()
                    AppLogger.i("$TAG: solved $host via trawl in ${System.currentTimeMillis() - t0} ms (valid ${((until[host] ?: 0) - System.currentTimeMillis()) / 60000} min)")
                    true
                } catch (t: Throwable) {
                    failedAt[host] = System.currentTimeMillis()
                    AppLogger.i("$TAG: trawl solve failed for $host: ${t.message} (retry in ${FAIL_RETRY_MS / 60000} min)")
                    false
                }
            }
        }
    }

    // per-instance views onto the shared jar (plugins read these)
    val savedCookies: MutableMap<String, Map<String, String>> get() = cookies
    val savedUserAgents: MutableMap<String, String> get() = agents

    fun getCookieHeaders(url: String): Headers {
        val host = try {
            URI(url).host
        } catch (e: Exception) {
            null
        }
        val builder = Headers.Builder()

        host?.let { h ->
            val cookieMap = cookies[h] ?: emptyMap()
            val userAgent = agents[h]

            if (!userAgent.isNullOrBlank()) {
                builder.add("user-agent", userAgent)
            }
            if (cookieMap.isNotEmpty()) {
                builder.add("cookie", cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
        }

        return builder.build()
    }

    /** The request with the stored clearance cookies + solver User-Agent applied. */
    private fun withClearance(request: Request, host: String): Request {
        val b = request.newBuilder()
        agents[host]?.takeIf { it.isNotBlank() }?.let { b.header("user-agent", it) }
        val existing = request.header("cookie")?.let { parseCookieMap(it) } ?: emptyMap()
        val all = existing + (cookies[host] ?: emptyMap())
        if (all.isNotEmpty()) b.header("cookie", all.entries.joinToString("; ") { "${it.key}=${it.value}" })
        return b.build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // known clearance: send it; re-solve once if Cloudflare rejects it
        if (valid(host)) {
            val r = chain.proceed(withClearance(request, host))
            if (!isChallenge(r)) return r
            r.close()
            cookies.remove(host); until.remove(host)
            return if (solve(host, request.url.scheme)) chain.proceed(withClearance(request, host)) else chain.proceed(request)
        }

        val response = chain.proceed(request)
        if (!isChallenge(response)) return response

        if (!canSolve(host)) {
            if (announced.add(host)) AppLogger.i("$TAG: $host is behind a Cloudflare challenge (not on CSBRIDGE_CF_SOLVE_HOSTS, passing through)")
            return response
        }
        response.close()
        return if (solve(host, request.url.scheme)) chain.proceed(withClearance(request, host)) else chain.proceed(request)
    }
}
