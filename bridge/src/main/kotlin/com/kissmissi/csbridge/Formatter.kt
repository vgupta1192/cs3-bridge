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
    private val lock = Any()

    @Volatile
    private var api: Value? = null

    data class Templates(val name: String, val description: String)
    data class Rendered(val name: String, val description: String)

    private fun script(name: String): String =
        javaClass.getResourceAsStream("/formatter/$name")
            ?.readBytes()?.toString(Charsets.UTF_8)
            ?: throw IllegalStateException("missing resource /formatter/$name")

    private fun ensure(): Value {
        api?.let { return it }
        synchronized(lock) {
            api?.let { return it }
            val ctx = Context.newBuilder("js")
                .option("js.ecmascript-version", "2023")
                .option("engine.WarnInterpreterOnly", "false")
                .build()
            val combined = script("csb-prelude.js") + "\n" +
                script("aiostreams-formatter.js") + "\n" +
                "var __PENGUPLAY_PRESETS = " + script("penguplay-presets.json") + ";\n" +
                script("csb-glue.js")
            ctx.eval("js", combined)
            val bound = ctx.getBindings("js").getMember("__CSB")
            require(bound != null && !bound.isNull) { "formatter glue did not define __CSB" }
            api = bound
            return bound
        }
    }

    /** Resolve a formatter config to templates; null = built-in naming. */
    fun templates(f: String, n: String?, d: String?): Templates? {
        val res = synchronized(lock) { ensure().getMember("templates").execute(f, n ?: "", d ?: "") }
        if (res.isNull) return null
        return mapper.readValue<Templates>(res.asString())
    }

    /** Render one stream; meta = raw ExtractorLink fields, the glue parses them. */
    fun render(t: Templates, metaJson: String, ctxJson: String): Rendered {
        val res = synchronized(lock) { ensure().getMember("render").execute(t.name, t.description, metaJson, ctxJson) }
        return mapper.readValue<Rendered>(res.asString())
    }

    /** Render many streams in ONE polyglot hop; null entries = use built-in naming.
     *  Per-serve formatter cost used to scale with stream count (one JS execute each);
     *  fresh answers blew way past the configured response deadline as a result. */
    fun renderBatch(t: Templates, items: List<Pair<String, String>>): List<Rendered?> {
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
            val res = synchronized(lock) { ensure().getMember("renderBatch").execute(t.name, t.description, arr).asString() }
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
        val res = synchronized(lock) { ensure().getMember("presets").execute() }
        return mapper.readValue(res.asString())
    }

    /** {ok, samples: [{label,name,description}], diagnostics: {name: [], description: []}} */
    fun preview(name: String, description: String): Map<String, Any?> {
        val samples = synchronized(lock) { ensure().getMember("preview").execute(name, description).asString() }
        val diag = synchronized(lock) { ensure().getMember("validate").execute(name, description).asString() }
        return linkedMapOf(
            "ok" to true,
            "samples" to mapper.readValue<List<Map<String, Any?>>>(samples),
            "diagnostics" to mapper.readValue<Map<String, Any?>>(diag),
        )
    }

    /** Fail-fast at boot (background thread): engine loads and renders. */
    fun warmup() {
        val t = templates("csb-minimal", null, null) ?: throw IllegalStateException("csb-minimal did not resolve")
        render(t, """{"provider":"Warmup","quality":1080,"url":"https://warmup/video.mkv"}""", "{}")
    }
}
