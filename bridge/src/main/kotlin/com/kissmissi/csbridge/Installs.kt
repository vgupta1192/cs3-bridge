package com.kissmissi.csbridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

    private fun fileFor(id: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(File(Cfg.dataDir, "installs"), "$name.json")
    }

    /** Stored config JSON for an install id, or null (caller falls back to the URL segment). */
    fun load(id: String): String? = runCatching {
        fileFor(id).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun save(id: String, configJson: String): Pair<Boolean, String> {
        // real b64 config segments run ~4 KB once big formatter templates are in
        if (id.length > 8000) return false to "install id too long"
        val root = runCatching { mapper.readTree(configJson) }.getOrNull()
            ?: return false to "config must be valid JSON"
        if (!root.isObject || !root.has("p") || !root.get("p").isObject) return false to "config needs a providers object \"p\""
        return runCatching {
            val f = fileFor(id)
            f.parentFile.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(configJson)
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) throw IllegalStateException("rename failed")
            }
            true to "saved"
        }.getOrElse { false to (it.message ?: "save failed") }
    }
}
