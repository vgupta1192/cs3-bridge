package com.kissmissi.csbridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

/**
 * AIOStreams custom-formatter engine (vendored bundle in resources/formatter,
 * same one Stream Master ships) running inside GraalJS. One shared Context
 * guarded by a lock — renders are pure string work and the engine caches
 * compiled templates, so this never approaches the scrape deadline. Everything
 * crossing the polyglot boundary is a JSON string, so no host access is
 * enabled on the context.
 */
object Formatter {
    private val mapper = jacksonObjectMapper()
    /**
     * Two engines: live first answers never queue behind background renders
     * (warmer, late merges, rescrapes). With one shared engine a warmer batch
     * held the lock for seconds and a live tap answered after the app had
     * already timed out (Broken pipe).
     */
    private class Engine(val name: String) {
        val lock = Any()
        @Volatile var api: Value? = null
    }
    private val liveEngine = Engine("live")
    private val bgEngine = Engine("background")
    private fun engine(bg: Boolean) = if (bg) bgEngine else liveEngine

    data class Templates(val name: String, val description: String)
    data class Rendered(val name: String, val description: String)

    private fun script(name: String): String =
        javaClass.getResourceAsStream("/formatter/$name")
            ?.readBytes()?.toString(Charsets.UTF_8)
            ?: throw IllegalStateException("missing resource /formatter/$name")

    private val combinedScript: String by lazy {
        script("csb-prelude.js") + "\n" +
            script("aiostreams-formatter.js") + "\n" +
            "var __PENGUPLAY_PRESETS = " + script("penguplay-presets.json") + ";\n" +
            script("csb-glue.js")
    }

    private fun ensure(e: Engine): Value {
        e.api?.let { return it }
        synchronized(e.lock) {
            e.api?.let { return it }
            val ctx = Context.newBuilder("js")
                .option("js.ecmascript-version", "2023")
                .option("engine.WarnInterpreterOnly", "false")
                .build()
            ctx.eval("js", combinedScript)
            val bound = ctx.getBindings("js").getMember("__CSB")
            require(bound != null && !bound.isNull) { "formatter glue did not define __CSB" }
            e.api = bound
            return bound
        }
    }

    private fun <T> call(bg: Boolean, block: (Value) -> T): T {
        val e = engine(bg)
        return synchronized(e.lock) { block(ensure(e)) }
    }

    /**
     * The same bundle under Node (V8 JIT) in a child process: GraalJS on a
     * stock JDK is interpreter-only, and on this VPS a 49-stream render took
     * 1.7 s idle and 23 s under swap pressure; V8 renders 50 in 35-90 ms.
     * GraalJS stays the fallback (no node binary, host crashed, call failed).
     * CSBRIDGE_FORMATTER=graal forces GraalJS.
     */
    private class NodeHost(val name: String) {
        val lock = Any()
        private var proc: Process? = null
        private var writer: java.io.BufferedWriter? = null
        private var reader: java.io.BufferedReader? = null
        private var nextId = 1L
        @Volatile var retryAt = 0L

        private fun start(): Boolean {
            if (System.currentTimeMillis() < retryAt) return false
            retryAt = System.currentTimeMillis() + 60_000
            return try {
                val dir = java.io.File(Cfg.dataDir, "formatter-node").apply { mkdirs() }
                for (f in listOf("csb-prelude.js", "aiostreams-formatter.js", "penguplay-presets.json", "csb-glue.js", "node-host.js")) {
                    java.io.File(dir, f).writeText(script(f))
                }
                val p = ProcessBuilder(nodeBin, "--max-old-space-size=96", java.io.File(dir, "node-host.js").path, dir.path)
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start()
                val r = p.inputStream.bufferedReader(Charsets.UTF_8)
                val w = p.outputStream.bufferedWriter(Charsets.UTF_8)
                val ready = r.readLine()
                if (ready == null || !ready.contains("ready")) { p.destroyForcibly(); false }
                else {
                    proc = p; reader = r; writer = w
                    com.lagradost.common.logging.AppLogger.i("Formatter: node host '$name' started (pid ${p.pid()})")
                    true
                }
            } catch (t: Throwable) {
                com.lagradost.common.logging.AppLogger.i("Formatter: node host '$name' unavailable (${t.message}), using GraalJS")
                false
            }
        }

        private fun stop() {
            runCatching { proc?.destroyForcibly() }
            proc = null; reader = null; writer = null
        }

        /** Result string, JS null as Kotlin null; throws when the host failed. */
        fun invoke(fn: String, args: List<String>): String? = synchronized(lock) {
            if (proc?.isAlive != true) { stop(); if (!start()) throw IllegalStateException("node host down") }
            val id = nextId++
            val p = proc!!
            // a stuck render must not hold the lock forever: kill the host,
            // readLine() then returns null and the caller falls back to GraalJS
            val watchdog = watchdogs.schedule({ p.destroyForcibly() }, 15, java.util.concurrent.TimeUnit.SECONDS)
            try {
                writer!!.write(mapper.writeValueAsString(mapOf("id" to id, "fn" to fn, "args" to args)))
                writer!!.write("\n")
                writer!!.flush()
                val line = reader!!.readLine() ?: run { stop(); throw IllegalStateException("node host exited") }
                val res = mapper.readTree(line)
                if (!res.path("ok").asBoolean(false)) throw IllegalStateException("node: " + res.path("e").asText(""))
                val v = res.get("v")
                if (v == null || v.isNull) null else v.asText()
            } catch (t: java.io.IOException) {
                stop(); throw t
            } finally {
                watchdog.cancel(false)
            }
        }
    }

    private val nodeBin: String = System.getenv("CSBRIDGE_NODE") ?: "node"
    private val nodeOn = (System.getenv("CSBRIDGE_FORMATTER") ?: "node") != "graal"
    private val watchdogs = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "formatter-watchdog").apply { isDaemon = true }
    }
    private val liveNode = NodeHost("live")
    private val bgNode = NodeHost("background")

    /** Call one __CSB function with string args: Node first, GraalJS on failure. */
    private fun js(bg: Boolean, fn: String, vararg args: String): String? {
        if (nodeOn) {
            try {
                return (if (bg) bgNode else liveNode).invoke(fn, args.toList())
            } catch (t: Throwable) {
                if (t.message?.startsWith("node: ") == true) throw t // the script itself failed: same in GraalJS
            }
        }
        return call(bg) { api -> api.getMember(fn).execute(*args).let { if (it.isNull) null else it.asString() } }
    }

    /** Resolve a formatter config to templates; null = built-in naming. */
    fun templates(f: String, n: String?, d: String?, bg: Boolean = false): Templates? {
        val res = js(bg, "templates", f, n ?: "", d ?: "") ?: return null
        return mapper.readValue<Templates>(res)
    }

    /** Render one stream; meta = raw ExtractorLink fields, the glue parses them. */
    fun render(t: Templates, metaJson: String, ctxJson: String): Rendered {
        val res = js(false, "render", t.name, t.description, metaJson, ctxJson)
        return mapper.readValue<Rendered>(res ?: "null")
    }

    /** Render many streams in ONE polyglot hop; null entries = use built-in naming.
     *  Per-serve formatter cost used to scale with stream count (one JS execute each);
     *  fresh answers blew way past the configured response deadline as a result. */
    fun renderBatch(t: Templates, items: List<Pair<String, String>>, bg: Boolean = false): List<Rendered?> {
        if (items.isEmpty()) return emptyList()
        // GraalJS runs interpreter-only on a stock JDK, so every render is real
        // CPU. A serve, its late merge and every rescrape re-render mostly the
        // same links — memoize per (template, link meta, context).
        val tk = (t.name + "\u0000" + t.description).hashCode().toString()
        val keys = items.map { tk + "\u0000" + it.first + "\u0000" + it.second }
        val out = arrayOfNulls<Rendered>(items.size)
        val miss = ArrayList<Int>()
        synchronized(memo) {
            keys.forEachIndexed { i, k -> val hit = memo[k]; if (hit != null) out[i] = hit else miss.add(i) }
        }
        if (miss.isNotEmpty()) {
            val arr = mapper.writeValueAsString(miss.map { listOf(items[it].first, items[it].second) })
            val res = js(bg, "renderBatch", t.name, t.description, arr) ?: "[]"
            val rendered = mapper.readValue<List<Rendered?>>(res)
            synchronized(memo) {
                miss.forEachIndexed { j, i ->
                    val r = rendered.getOrNull(j)
                    out[i] = r
                    if (r != null) memo[keys[i]] = r
                }
            }
        }
        return out.toList()
    }

    private val memo = object : java.util.LinkedHashMap<String, Rendered>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Rendered>?): Boolean = size > 6000
    }

    /** {presets: [{id,label,family,name,description}], fields: {section: [props]}} */
    fun presets(): Map<String, Any?> {
        val res = js(false, "presets") ?: "{}"
        return mapper.readValue(res)
    }

    /** {ok, samples: [{label,name,description}], diagnostics: {name: [], description: []}} */
    fun preview(name: String, description: String): Map<String, Any?> {
        val samples = js(false, "preview", name, description) ?: "[]"
        val diag = js(false, "validate", name, description) ?: "{}"
        return linkedMapOf(
            "ok" to true,
            "samples" to mapper.readValue<List<Map<String, Any?>>>(samples),
            "diagnostics" to mapper.readValue<Map<String, Any?>>(diag),
        )
    }

    /**
     * Boot: render a realistic batch with every saved install's templates on
     * both engines. GraalJS runs interpreter-only, so a cold engine took ~8 s
     * on the first real answer after a restart (past the app's timeout).
     */
    fun warmInstalls() {
        val samples = (1..12).map { i ->
            val q = listOf(2160, 1080, 720, 480)[i % 4]
            """{"provider":"Warmup","linkName":"Warmup $q","source":"Server$i","quality":$q,"url":"https://warmup.example/Movie.2024.${q}p.WEB-DL.Hindi.English.x264-$i.mkv","kind":"VIDEO","lang":"hi"}""" to
                """{"mediaType":"movie","title":"Warmup","year":2024}"""
        }
        for (cfg in Installs.all()) {
            val fmt = FmtCfg.fromRaw(cfg["fmt"])
            for (bg in listOf(false, true)) runCatching {
                val t = templates(fmt.f, fmt.n, fmt.d, bg) ?: return@runCatching
                renderBatch(t, samples, bg)
            }
        }
        // the warm-up samples must not crowd the memo
        synchronized(memo) { memo.clear() }
    }

    /** Fail-fast at boot (background thread): engine loads and renders. */
    fun warmup() {
        val t = templates("csb-minimal", null, null) ?: throw IllegalStateException("csb-minimal did not resolve")
        render(t, """{"provider":"Warmup","quality":1080,"url":"https://warmup/video.mkv"}""", "{}")
        renderBatch(t, listOf("""{"provider":"Warmup","quality":720,"url":"https://warmup/v2.mkv"}""" to "{}"), bg = true)
    }
}
