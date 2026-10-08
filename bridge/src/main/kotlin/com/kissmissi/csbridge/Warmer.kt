package com.kissmissi.csbridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Catalog warmer, modelled on Stream Master's catalogWarmer.js: walks the
 * comprehensive-catalog rows in manifest order (top Cfg.warmTop per row, in
 * waves of Cfg.warmWave over groups of Cfg.warmGroup catalogs), series as
 * S1E1, and scrapes every title into the stream cache before anyone taps it.
 * Warmed for each install config that served a stream in the last 7 days
 * (cache entries are keyed by config fingerprint). Titles written within
 * Cfg.warmFreshMs are skipped, so restarts resume where the run left off;
 * the warmer pauses while live requests are coming in.
 */
object Warmer {
    private val mapper = jacksonObjectMapper()
    private const val USED_TTL = 7L * 24 * 3600 * 1000
    private const val PLAN_REBUILD_MS = 6L * 3600 * 1000

    @Volatile private var lastLiveAt = 0L
    private val lastUsedWrite = ConcurrentHashMap<String, Long>()

    // stats
    @Volatile private var running = false
    @Volatile private var planSize = 0
    @Volatile private var planBuiltAt = 0L
    @Volatile private var catalogs: List<Map<String, Any?>> = emptyList()
    @Volatile private var lastError: String? = null
    private val done = AtomicInteger(0)
    private val skippedFresh = AtomicInteger(0)
    private val failed = AtomicInteger(0)
    private val current = ConcurrentHashMap.newKeySet<String>()

    data class Item(val type: String, val imdbId: String, val name: String?, val catalog: String, val rank: Int)

    /** Called on every live stream request: pauses the warmer, remembers the install. */
    fun noteLive(fp: String, segment: String) {
        lastLiveAt = System.currentTimeMillis()
        val now = System.currentTimeMillis()
        val prev = lastUsedWrite[fp]
        if (prev == null || now - prev > 3600_000) {
            lastUsedWrite[fp] = now
            runCatching { Store.put("used:$fp", segment) }
        }
    }

    fun stats(): Map<String, Any?> = linkedMapOf(
        "enabled" to Cfg.warmEnabled,
        "running" to running,
        "catalogUrl" to Cfg.warmCatalogUrl,
        "top" to Cfg.warmTop,
        "concurrency" to Cfg.warmConcurrency,
        "dayConcurrency" to Cfg.warmDayConcurrency,
        "nightHours" to "${Cfg.warmNightHours} ${Cfg.warmTz}",
        "night" to isNight(),
        "installs" to runCatching { targets().size }.getOrDefault(0),
        "planTitles" to planSize,
        "planBuiltAt" to planBuiltAt,
        "warmedThisRound" to done.get(),
        "skippedFresh" to skippedFresh.get(),
        "failed" to failed.get(),
        "current" to current.toList(),
        "catalogs" to catalogs,
        "lastError" to lastError,
    )

    fun isNight(): Boolean {
        val (a, b) = Cfg.warmNightHours.split('-').mapNotNull { it.trim().toIntOrNull() }.let { (it.getOrNull(0) ?: 1) to (it.getOrNull(1) ?: 8) }
        val h = java.time.ZonedDateTime.now(Cfg.warmTz).hour
        return if (a <= b) h in a until b else (h >= a || h < b)
    }

    private val slotLock = Object()
    private var inUse = 0

    /** Day: Cfg.warmDayConcurrency titles at once; night: Cfg.warmConcurrency. */
    private fun acquireSlot() {
        synchronized(slotLock) {
            while (inUse >= (if (isNight()) Cfg.warmConcurrency else Cfg.warmDayConcurrency).coerceAtLeast(1)) slotLock.wait(10_000)
            inUse++
        }
    }

    private fun releaseSlot() {
        synchronized(slotLock) { inUse--; slotLock.notifyAll() }
    }

    private fun waitForQuiet() {
        while (System.currentTimeMillis() - lastLiveAt < Cfg.warmLiveQuietMs) Thread.sleep(2000)
    }

    private fun getJson(url: String): com.fasterxml.jackson.databind.JsonNode? =
        runBlocking { Resolver.httpGet(url) }?.let { runCatching { mapper.readTree(it) }.getOrNull() }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Top N metas of one catalog, following skip= pages. */
    private fun fetchTop(type: String, id: String, n: Int): List<com.fasterxml.jackson.databind.JsonNode> {
        val out = ArrayList<com.fasterxml.jackson.databind.JsonNode>()
        val seen = HashSet<String>()
        var skip = 0
        while (out.size < n && skip < n + 200) {
            val path = if (skip > 0) "/skip=$skip" else ""
            val metas = getJson("${Cfg.warmCatalogUrl}/catalog/${enc(type)}/${enc(id)}$path.json")?.path("metas") ?: break
            if (!metas.isArray || metas.size() == 0) break
            var added = 0
            for (m in metas) {
                val mid = m.path("id").asText("")
                if (mid.isBlank() || !seen.add(mid)) continue
                out.add(m); added++
                if (out.size >= n) break
            }
            if (added == 0) break
            skip += metas.size()
        }
        return out
    }

    /** Same ordering as Stream Master: wave 1 of catalog group 1, group 2, ..., then wave 2, ... */
    private fun buildPlan(): List<Item> {
        val manifest = getJson("${Cfg.warmCatalogUrl}/manifest.json") ?: throw IllegalStateException("catalog manifest unreachable")
        var cats = manifest.path("catalogs").filter { c ->
            val type = c.path("type").asText("")
            val searchOnly = c.path("extra").any { it.path("name").asText() == "search" && it.path("isRequired").asBoolean(false) }
            (type == "movie" || type == "series") && !searchOnly
        }
        if (Cfg.warmCatalogFilter.isNotEmpty()) cats = cats.filter { it.path("id").asText() in Cfg.warmCatalogFilter }
        val lists = cats.mapNotNull { c ->
            runCatching { c to fetchTop(c.path("type").asText(), c.path("id").asText(), Cfg.warmTop) }
                .onFailure { AppLogger.e("Warmer: catalog ${c.path("id").asText()} failed: ${it.message}") }
                .getOrNull()
        }
        catalogs = lists.map { (c, metas) -> mapOf("id" to c.path("id").asText(), "type" to c.path("type").asText(), "titles" to metas.size) }
        val plan = ArrayList<Item>()
        val seen = HashSet<String>()
        var start = 0
        while (start < Cfg.warmTop) {
            var g = 0
            while (g < lists.size) {
                val group = lists.subList(g, minOf(g + Cfg.warmGroup, lists.size))
                for (i in start until start + Cfg.warmWave) for ((c, metas) in group) {
                    val m = metas.getOrNull(i) ?: continue
                    val type = c.path("type").asText()
                    val mid = m.path("id").asText()
                    if (!mid.startsWith("tt") || !seen.add("$type:$mid")) continue
                    plan.add(Item(type, mid, m.path("name").asText(null), c.path("id").asText(), i + 1))
                }
                g += Cfg.warmGroup
            }
            start += Cfg.warmWave
        }
        return plan
    }

    /** Install configs (fingerprint -> segment) that served a stream in the last 7 days. */
    private fun targets(): List<Pair<String, String>> =
        Store.listPrefix("used:", USED_TTL).map { (k, seg) -> k.removePrefix("used:") to seg }
            .filter { (fp, seg) -> Installs.fingerprint(seg) == fp } // config changed since: skip old fingerprint

    private fun warmOne(item: Item, fp: String, seg: String) {
        val kind = item.type
        val id = if (kind == "series") "${item.imdbId}:1:1" else item.imdbId
        val cacheKey = "streams2:$fp:$kind:$id"
        val age = Store.ageOf(cacheKey)
        if ((age != null && age < Cfg.warmFreshMs) || Streams.isScraping(cacheKey)) { skippedFresh.incrementAndGet(); return }
        waitForQuiet()
        acquireSlot()
        val label = "${item.name ?: item.imdbId} ($kind, ${item.catalog} #${item.rank})"
        current.add(label)
        try {
            val cfg = BridgeConfig.decode(seg)
            runBlocking { Streams.warm(cfg, kind, id, cacheKey) }
            done.incrementAndGet()
        } catch (t: Throwable) {
            failed.incrementAndGet()
            AppLogger.i("Warmer: $label failed: ${t.message}")
        } finally {
            current.remove(label)
            releaseSlot()
        }
    }

    fun start() {
        if (!Cfg.warmEnabled) { AppLogger.i("Warmer: disabled (CSBRIDGE_WARM_ENABLED=false)"); return }
        Thread {
            // let boot loading and the first sync settle
            runCatching { Thread.sleep(120_000) }
            while (true) {
                try {
                    running = true
                    done.set(0); skippedFresh.set(0); failed.set(0)
                    val plan = buildPlan()
                    planSize = plan.size; planBuiltAt = System.currentTimeMillis(); lastError = null
                    val tg = targets()
                    AppLogger.i("Warmer: ${plan.size} titles from ${catalogs.size} catalogs x ${tg.size} installs")
                    val deadlineAt = System.currentTimeMillis() + PLAN_REBUILD_MS
                    val sem = Semaphore(Cfg.warmConcurrency.coerceAtLeast(1))
                    runBlocking {
                        plan.map { item ->
                            async(kotlinx.coroutines.Dispatchers.IO) {
                                sem.withPermit {
                                    if (System.currentTimeMillis() < deadlineAt) for ((fp, seg) in tg) warmOne(item, fp, seg)
                                }
                            }
                        }.awaitAll()
                    }
                    AppLogger.i("Warmer: round done: ${done.get()} warmed, ${skippedFresh.get()} fresh, ${failed.get()} failed")
                } catch (t: Throwable) {
                    lastError = t.message
                    AppLogger.e("Warmer: round failed: ${t.message}")
                } finally {
                    running = false
                }
                // next round picks up whatever has gone stale meanwhile
                runCatching { Thread.sleep(30L * 60 * 1000) }
            }
        }.apply { isDaemon = true; name = "catalog-warmer" }.start()
    }
}
