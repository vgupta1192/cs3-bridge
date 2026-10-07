package com.kissmissi.csbridge

import com.lagradost.common.logging.AppLogger

fun main() {
    AppLogger.i("CloudStream Bridge ${Cfg.version} starting…")
    Boot.init()
    HttpApi.start()
    Repos.startBackgroundSync()
    Thread {
        while (true) {
            try { Thread.sleep(24L * 3600 * 1000) } catch (_: InterruptedException) {}
            runCatching { Store.cleanup() }
        }
    }.apply { isDaemon = true; name = "kv-cleanup" }.start()
    AppLogger.i("CloudStream Bridge ready")
}
