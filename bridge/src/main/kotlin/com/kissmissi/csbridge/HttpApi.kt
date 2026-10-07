package com.kissmissi.csbridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.common.logging.AppLogger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors

object HttpApi {
    private val mapper = jacksonObjectMapper()
    private const val META_TTL = 12L * 3600 * 1000
    private val started = System.currentTimeMillis()


    fun start() {
        val server = HttpServer.create(InetSocketAddress("0.0.0.0", Cfg.port), 256)
        server.executor = Executors.newFixedThreadPool(24)
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } catch (t: Throwable) {
                AppLogger.e("HTTP error on ${exchange.requestURI.path}", t)
                runCatching {
                    sendJson(exchange, 500, mapOf("error" to (t.message ?: "internal error")))
                }
            } finally {
                exchange.close()
            }
        }
        server.start()
        AppLogger.i("HTTP server listening on :${Cfg.port}")
    }


    private fun sendJson(ex: HttpExchange, code: Int, body: Any?) {
        val bytes = mapper.writeValueAsBytes(body)
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }


    private fun sendHtml(ex: HttpExchange, html: String) {
        val bytes = html.toByteArray()
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }


    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path ?: return
        val segs = path.trim('/').split('/').filter { it.isNotBlank() }
        AppLogger.i("HTTP ${ex.requestMethod} $path")

        when {
            segs.isEmpty() -> sendHtml(ex, Ui.page())
            segs[0] == "ping" -> sendJson(ex, 200, ping())
            segs[0] == "configure" -> sendHtml(ex, Ui.page())
            segs[0] == "logo.svg" -> sendSvg(ex)
            segs[0] == "api" && segs.size > 1 -> api(ex, segs)
            segs[0] == "manifest.json" -> sendJson(ex, 200, manifest(BridgeConfig.decode("")))
            segs.size >= 2 && segs[1] == "manifest.json" -> sendJson(ex, 200, manifest(BridgeConfig.decode(segs[0])))
            segs.size >= 4 && segs[1] == "stream" -> stream(ex, segs)
            segs.size >= 4 && segs[1] == "catalog" -> catalog(ex, segs)
            segs.size >= 4 && segs[1] == "meta" -> meta(ex, segs)
            else -> sendJson(ex, 404, mapOf("error" to "not found"))
        }
    }


    private fun ping(): Map<String, Any?> = linkedMapOf(
        "ok" to true,
        "addon" to Cfg.ADDON_NAME,
        "version" to Cfg.version,
        "uptime_s" to ((System.currentTimeMillis() - started) / 1000),
        "plugins_loaded" to Repos.plugins.values.count { it.loaded },
        "plugins_total" to Repos.plugins.size,
        "syncing" to Repos.isSyncing(),
    )


    private fun api(ex: HttpExchange, segs: List<String>) {
        val q = ex.requestURI.query ?: ""
        val qp = q.split("&").mapNotNull {
            val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
        when (segs.getOrNull(1)) {
            "repos" -> when (segs.getOrNull(2)) {
                "add" -> {
                    val res = Repos.addRepo(qp["name"] ?: "", qp["url"] ?: "")
                    sendJson(ex, 200, mapOf("ok" to res.first, "message" to res.second))
                }
                "remove" -> {
                    val res = Repos.removeRepo(qp["url"] ?: "")
                    sendJson(ex, 200, mapOf("ok" to res.first, "message" to res.second))
                }
                else -> sendJson(ex, 200, reposJson())
            }
            "health" -> {
                val started = Repos.startHealthCheck()
                sendJson(ex, 200, mapOf("ok" to true, "running" to started))
            }
            "resync" -> {
                val force = ex.requestURI.query?.contains("force") == true
                Thread {
                    try { runBlocking { Repos.sync(force) } } catch (t: Throwable) {
                        AppLogger.e("resync failed", t)
                    }
                }.apply { isDaemon = true; name = "manual-resync" }.start()
                sendJson(ex, 200, mapOf("ok" to true, "message" to "resync started"))
            }
            else -> sendJson(ex, 404, mapOf("error" to "unknown api"))
        }
    }


    private fun reposJson(): Map<String, Any?> = linkedMapOf(
        "repos" to Repos.loadRepos().map { repo ->
            linkedMapOf(
                "name" to repo.name,
                "url" to repo.pluginsUrl,
                "description" to repo.description,
                "plugins" to Repos.plugins.values.filter { it.repo == repo.name }.sortedBy { it.internalName }
                    .map { p ->
                        val h = Repos.healthOf(p.internalName)
                        linkedMapOf(
                            "internalName" to p.internalName,
                            "name" to p.name,
                            "description" to p.description,
                            "version" to p.version,
                            "iconUrl" to p.iconUrl,
                            "language" to p.language,
                            "tvTypes" to p.tvTypes,
                            "loaded" to p.loaded,
                            "error" to p.error,
                            "providers" to p.providerNames,
                            "health" to h.status,
                            "lastOk" to h.lastOk,
                            "lastCheck" to h.lastCheck,
                        )
                    },
            )
        },
        "syncing" to Repos.isSyncing(),
        "lastSync" to Repos.lastSyncAt(),
        "healthRunning" to Repos.isHealthRunning(),
    )


    private fun manifest(cfg: BridgeConfig): Map<String, Any?> {
        val enabled = Repos.enabledProviders(cfg.providers)
        val catalogs = ArrayList<Map<String, Any?>>()
        if (cfg.catalogs) {
            for ((info, provs) in enabled) {
                for (prov in provs) {
                    val rows = runCatching { prov.mainPage.filter { it.name.isNotBlank() && it.data.isNotBlank() } }
                        .getOrDefault(emptyList())
                    if (rows.isEmpty()) continue
                    catalogs.add(
                        linkedMapOf(
                            "type" to "other",
                            "id" to "csb_" + info.internalName,
                            "name" to prov.name,
                            "extra" to listOf(
                                linkedMapOf("name" to "genre", "isRequired" to false, "options" to rows.map { it.name }),
                                linkedMapOf("name" to "skip", "isRequired" to false),
                                linkedMapOf("name" to "search", "isRequired" to false),
                            ),
                        )
                    )
                }
            }
        }
        return linkedMapOf(
            "id" to Cfg.ADDON_ID,
            "version" to Cfg.version,
            "name" to Cfg.ADDON_NAME,
            "description" to "CloudStream extension bridge — streams from ${Cfg.repos.joinToString(", ") { it.name }} repos",
            "logo" to null,
            "types" to listOf("movie", "series", "other"),
            "resources" to if (cfg.catalogs) listOf("catalog", "meta", "stream") else listOf("stream"),
            "idPrefixes" to listOf("tt", "csb"),
            "catalogs" to catalogs,
            "behaviorHints" to mapOf("configurable" to true),
        )
    }


    private fun stream(ex: HttpExchange, segs: List<String>) {
        val cfg = BridgeConfig.decode(segs[0])
        val kind = segs[2] // movie | series | other
        val id = URLDecoder.decode(segs.drop(3).joinToString("/").removeSuffix(".json"), "UTF-8")
        val cacheKey = "streams:${segs[0]}:$kind:$id"
        val cached = Store.get(cacheKey, 6L * 3600 * 1000)
        if (cached != null) {
            sendJson(ex, 200, mapper.readValue<Map<String, Any?>>(cached))
            return
        }
        val streams = Streams.streamsFor(cfg, kind, id)
        val body = linkedMapOf<String, Any?>("streams" to streams)
        if (streams.isNotEmpty()) Store.put(cacheKey, mapper.writeValueAsString(body))
        sendJson(ex, 200, body)
    }


    private fun tvTypeToStremio(t: TvType?): String = when (t) {
        TvType.Movie, TvType.AnimeMovie -> "movie"
        TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA, TvType.AsianDrama, TvType.Documentary -> "series"
        else -> "other"
    }


    private fun metaPreview(result: com.lagradost.cloudstream3.SearchResponse, provName: String): Map<String, Any?> {
        return linkedMapOf(
            "id" to "csb:${provName}:${Streams.b64(result.url)}",
            "type" to tvTypeToStremio(result.type),
            "name" to result.name,
            "poster" to result.posterUrl,
            "posterShape" to "poster",
            "description" to result.apiName,
        )
    }


    private fun catalog(ex: HttpExchange, segs: List<String>) {
        val cfg = BridgeConfig.decode(segs[0])
        val kind = segs[2]
        val catId = segs[3]
        val extraRaw = segs.drop(4).joinToString("/").removeSuffix(".json")
        val extra = extraRaw.split("&").mapNotNull {
            val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
        val genre = extra["genre"]
        val skip = extra["skip"]?.toIntOrNull() ?: 0
        val search = extra["search"]?.takeIf { it.isNotBlank() }
        val provName = catId.removePrefix("csb_")

        val cacheKey = "catalog:${segs[0]}:$catId:${extraRaw}"
        Store.get(cacheKey, 2L * 3600 * 1000)?.let {
            sendJson(ex, 200, mapper.readValue<Map<String, Any?>>(it)); return
        }

        val metas = runBlocking {
            withTimeout(Cfg.providerTimeoutMs) {
                val enabled = Repos.enabledProviders(cfg.providers)
                val entry = enabled.firstOrNull { it.first.internalName == provName } ?: return@withTimeout emptyList()
                val (info, provs) = entry
                val prov = provs.firstOrNull() ?: return@withTimeout emptyList()
                try {
                    if (search != null) {
                        val results = prov.search(search, skip + 1)?.items ?: emptyList()
                        results.map { metaPreview(it, info.internalName) }
                    } else {
                        val row = prov.mainPage.firstOrNull { it.name == genre } ?: prov.mainPage.firstOrNull {
                            it.name.isNotBlank() && it.data.isNotBlank()
                        } ?: return@withTimeout emptyList()
                        val resp = prov.getMainPage(skip + 1, MainPageRequest(row.name, row.data, row.horizontalImages))
                        resp?.items?.flatMap { it.list }?.map { metaPreview(it, info.internalName) } ?: emptyList()
                    }
                } catch (t: Throwable) {
                    AppLogger.e("catalog error $catId: ${t.message}")
                    emptyList()
                }
            }
        }
        val body = linkedMapOf<String, Any?>("metas" to metas)
        if (metas.isNotEmpty()) Store.put(cacheKey, mapper.writeValueAsString(body))
        sendJson(ex, 200, body)
    }


    private fun meta(ex: HttpExchange, segs: List<String>) {
        val cfg = BridgeConfig.decode(segs[0])
        val kind = segs[2]
        val id = URLDecoder.decode(segs.drop(3).joinToString("/").removeSuffix(".json"), "UTF-8")
        val parts = id.split(":")
        if (parts.size < 3 || parts[0] != "csb") {
            sendJson(ex, 404, mapOf("error" to "unsupported meta id")); return
        }
        val provName = parts[1]
        val url = runCatching { Streams.unb64(parts[2]) }.getOrNull()
        if (url == null) {
            sendJson(ex, 404, mapOf("error" to "bad id")); return
        }

        val cacheKey = "meta:${cfg.providers.hashCode()}:$id"
        Store.get(cacheKey, META_TTL)?.let {
            sendJson(ex, 200, mapper.readValue<Map<String, Any?>>(it)); return
        }

        val result = runBlocking {
            withTimeout(Cfg.providerTimeoutMs) {
                val enabled = Repos.enabledProviders(cfg.providers)
                val entry = enabled.firstOrNull { it.first.internalName == provName } ?: return@withTimeout null
                val prov = entry.second.firstOrNull() ?: return@withTimeout null
                try {
                    buildMeta(prov, provName, url)
                } catch (t: Throwable) {
                    AppLogger.e("meta error $id: ${t.message}")
                    null
                }
            }
        }
        if (result == null) {
            sendJson(ex, 404, mapOf("error" to "meta not found")); return
        }
        Store.put(cacheKey, mapper.writeValueAsString(result))
        sendJson(ex, 200, result)
    }


    private suspend fun buildMeta(prov: MainAPI, provName: String, url: String): Map<String, Any?>? {
        val resp = prov.load(url) ?: return null
        val csbId = "csb:$provName:${Streams.b64(url)}"
        val meta = LinkedHashMap<String, Any?>()
        val type = when (resp) {
            is MovieLoadResponse -> "movie"
            is TvSeriesLoadResponse -> "series"
            else -> tvTypeToStremio(resp.type)
        }
        meta["id"] = csbId
        meta["type"] = type
        meta["name"] = resp.name
        meta["poster"] = resp.posterUrl
        meta["background"] = (resp.backgroundPosterUrl ?: resp.posterUrl)
        meta["description"] = resp.plot
        meta["releaseInfo"] = resp.year?.toString()
        meta["runtime"] = resp.duration?.toString()
        meta["genres"] = resp.tags
        meta["cast"] = resp.actors?.mapNotNull { runCatching { it.actor.name }.getOrNull() }
        if (resp is TvSeriesLoadResponse) {
            meta["videos"] = resp.episodes.mapIndexed { idx, ep ->
                val s = ep.season ?: 1
                val e = ep.episode ?: (idx + 1)
                linkedMapOf(
                    "id" to "csb:$provName:${Streams.b64(url)}:$s:$e",
                    "season" to s,
                    "episode" to e,
                    "name" to (ep.name ?: "Episode $e"),
                    "thumbnail" to ep.posterUrl,
                    "description" to ep.description,
                    "released" to ep.date?.let { java.time.Instant.ofEpochMilli(it).toString() },
                )
            }
        }
        return mapOf("meta" to meta)
    }


    private fun sendSvg(ex: HttpExchange) {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="14" fill="#8b5cf6"/><text x="32" y="42" font-family="Arial" font-size="30" font-weight="bold" fill="#fff" text-anchor="middle">CS</text></svg>"""
        val bytes = svg.toByteArray()
        ex.responseHeaders.add("Content-Type", "image/svg+xml")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }
}
