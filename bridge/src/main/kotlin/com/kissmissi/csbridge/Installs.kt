package com.kissmissi.csbridge

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

/**
 * Server-side install configs. The manifest URL segment is a stable install id:
 * the first POST pins the config server-side (data/installs/<sha256(id)>.json)
 * and from then on [BridgeConfig.decode] prefers the stored config over whatever
 * the URL embeds — so Nuvio/Stremio installs never need re-adding when the user
 * edits anything on the configure page; apps pick manifest-visible changes up on
 * their next manifest refetch, stream behavior changes immediately.
 */
object Installs {
    private val mapper = jacksonObjectMapper()
    private val dir get() = File(Cfg.dataDir, "installs")
    private val lock = Any()

    private fun sha256(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun fileFor(id: String): File = File(dir, "${sha256(id)}.json")

    /** Stored config JSON for an install id, or null (caller falls back to the URL segment). */
    fun load(id: String): String? = runCatching {
        fileFor(id).takeIf { it.exists() }?.readText()
    }.getOrNull()

    /**
     * Short stable fingerprint of the effective config behind an install id.
     * Stream caches and in-flight scrapes are keyed by it, so two installs with
     * the same settings (phone + browser) share one scrape and one cache entry,
     * and any config change naturally misses the old cache.
     */
    fun fingerprint(id: String): String = sha256(load(id) ?: "seg:$id").take(16)

    /** Every saved install config (parsed). */
    fun all(): List<Map<String, Any?>> = runCatching {
        dir.listFiles { f -> f.name.endsWith(".json") }?.mapNotNull { f ->
            runCatching { mapper.readValue<Map<String, Any?>>(f) }.getOrNull()
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun write(f: File, json: String) {
        f.parentFile.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(f)) {
            f.delete()
            if (!tmp.renameTo(f)) throw IllegalStateException("rename failed")
        }
    }

    fun save(id: String, configJson: String): Pair<Boolean, String> {
        // real b64 config segments run ~4 KB once big formatter templates are in
        if (id.length > 8000) return false to "install id too long"
        val root = runCatching { mapper.readTree(configJson) }.getOrNull()
            ?: return false to "config must be valid JSON"
        if (!root.isObject || !root.has("p") || !root.get("p").isObject) return false to "config needs a providers object \"p\""
        return runCatching {
            synchronized(lock) { write(fileFor(id), configJson) }
            true to "saved"
        }.getOrElse { false to (it.message ?: "save failed") }
    }

    /**
     * Add [names] to the providers map of every saved install (only keys that are
     * missing — a source the user switched off stays off). Everything else in
     * each record is kept byte-for-byte as parsed. Returns installs changed.
     */
    fun enableEverywhere(names: Collection<String>): Int = synchronized(lock) {
        var changed = 0
        dir.listFiles { f -> f.name.endsWith(".json") }?.forEach { f ->
            runCatching {
                val root = mapper.readTree(f) as? ObjectNode ?: return@runCatching
                val p = root.get("p") as? ObjectNode ?: return@runCatching
                var dirty = false
                for (n in names) if (!p.has(n)) { p.put(n, 1); dirty = true }
                if (dirty) {
                    write(f, mapper.writeValueAsString(root))
                    changed++
                }
            }
        }
        changed
    }
}
