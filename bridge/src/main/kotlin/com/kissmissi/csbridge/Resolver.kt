@file:OptIn(com.lagradost.cloudstream3.Prerelease::class)

package com.kissmissi.csbridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TitleInfo(val name: String, val year: Int?, val type: String, val imdbId: String)

object Resolver {
    private val mapper = jacksonObjectMapper()
    private const val RESOLVE_TTL = 7L * 24 * 3600 * 1000
    private const val FAIL_TTL = 10L * 60 * 1000

    // figure out movie/series type from the requested path segment
    private fun metaUrl(kind: String, imdb: String) =
        "https://v3-cinemeta.strem.io/meta/$kind/$imdb.json"

    suspend fun resolve(kind: String, imdb: String): TitleInfo? {
        val key = "resolve:$kind:$imdb"
        Store.get(key, RESOLVE_TTL)?.let { return runCatching { mapper.readValue<TitleInfo>(it) }.getOrNull() }
        // short negative cache: a Cinemeta+TMDB outage otherwise re-fetches both
        // on every tap; recovered resolves need to be picked up quickly, so this
        // is minutes, not the 7-day success TTL
        val failKey = "resolve-fail:$kind:$imdb"
        if (Store.get(failKey, FAIL_TTL) != null) return null
        val info = fetch(kind, imdb)
        if (info != null) Store.put(key, mapper.writeValueAsString(info)) else Store.put(failKey, "1")
        return info
    }

    private suspend fun fetch(kind: String, imdb: String): TitleInfo? {
        // 1) Cinemeta
        try {
            val body = httpGet(metaUrl(kind, imdb))
            if (body != null) {
                val root = mapper.readTree(body)
                val meta = root.get("meta")
                if (meta != null && meta.hasNonNull("name")) {
                    val release = meta.path("releaseInfo").asText("")
                    val year = release.split(Regex("[^0-9]")).firstOrNull { it.length == 4 }?.toIntOrNull()
                    return TitleInfo(meta.get("name").asText(), year, kind, imdb)
                }
            }
        } catch (_: Exception) {}

        // 2) TMDB find by imdb id (fallback for Cinemeta-unreachable titles)
        val key2 = Cfg.tmdbKey ?: return null
        try {
            val body = httpGet("https://api.themoviedb.org/3/find/$imdb?api_key=$key2&external_source=imdb_id")
                ?: return null
            val root = mapper.readTree(body)
            val arr = if (kind == "movie") root.path("movie_results") else root.path("tv_results")
            if (arr.isArray && arr.size() > 0) {
                val m = arr.get(0)
                val name = m.path("name").asText(m.path("title").asText(""))
                if (name.isNotBlank()) {
                    val date = m.path("release_date").asText(m.path("first_air_date").asText(""))
                    val year = date.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
                    return TitleInfo(name, year, kind, imdb)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = okhttp3.Request.Builder().url(url)
                .header("user-agent", com.lagradost.cloudstream3.USER_AGENT)
                .build()
            com.lagradost.cloudstream3.app.baseClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) { null }
    }
}
