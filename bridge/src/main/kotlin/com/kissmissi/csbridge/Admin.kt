package com.kissmissi.csbridge

import com.lagradost.common.logging.AppLogger
import com.sun.net.httpserver.HttpExchange
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Admin token for the endpoints that change server state or make it fetch and
 * run code (repo add/remove, resync, health probes). Taken from
 * CSBRIDGE_ADMIN_TOKEN, else read from (or generated into) data/admin-token.
 * Clients send it in the [HEADER] header; a custom header also forces a CORS
 * preflight, which this server never approves, so other origins can't call
 * these endpoints from a visitor's browser.
 */
object Admin {
    const val HEADER = "X-CSB-Admin"

    private val tokenFile get() = File(Cfg.dataDir, "admin-token")

    private val token: String by lazy {
        System.getenv("CSBRIDGE_ADMIN_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }?.let { return@lazy it }
        runCatching { tokenFile.readText().trim() }.getOrNull()?.takeIf { it.length >= 16 }?.let { return@lazy it }
        val fresh = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        Cfg.dataDir.mkdirs()
        tokenFile.writeText(fresh + "\n")
        runCatching { tokenFile.setReadable(false, false); tokenFile.setReadable(true, true) }
        AppLogger.i("Admin: generated a new admin token in ${tokenFile.absolutePath} (set CSBRIDGE_ADMIN_TOKEN to choose your own)")
        fresh
    }

    /** Resolve the token at startup so a generated one is logged before the first request. */
    fun init() { token }

    fun isAuthorized(ex: HttpExchange): Boolean {
        val given = ex.requestHeaders.getFirst(HEADER)?.trim() ?: return false
        return MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
    }
}
