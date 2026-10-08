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

    /** Resolve a formatter config to templates; null = built-in naming. */
    fun templates(f: String, n: String?, d: String?, bg: Boolean = false): Templates? {
        val res = call(bg) { it.getMember("templates").execute(f, n ?: "", d ?: "") }
        if (res.isNull) return null
        return mapper.readValue<Templates>(res.asString())
    }

    /** Render one stream; meta = raw ExtractorLink fields, the glue parses them. */
    fun render(t: Templates, metaJson: String, ctxJson: String): Rendered {
        val res = call(false) { it.getMember("render").execute(t.name, t.description, metaJson, ctxJson) }
        return mapper.readValue<Rendered>(res.asString())
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
            val res = call(bg) { it.getMember("renderBatch").execute(t.name, t.description, arr).asString() }
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
        val res = call(false) { it.getMember("presets").execute() }
        return mapper.readValue(res.asString())
    }

    /** {ok, samples: [{label,name,description}], diagnostics: {name: [], description: []}} */
    fun preview(name: String, description: String): Map<String, Any?> {
        val samples = call(false) { it.getMember("preview").execute(name, description).asString() }
        val diag = call(false) { it.getMember("validate").execute(name, description).asString() }
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
