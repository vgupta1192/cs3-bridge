package com.kissmissi.csbridge

import com.lagradost.common.logging.AppLogger

fun main() {
    AppLogger.i("CloudStream Bridge ${Cfg.version} starting…")
    Boot.init()
    // cached stream lists embed the formatter output — flush them whenever the
    // deployed version changes so old formatting never lingers past a deploy
    runCatching {
        if (Store.get("sys:version", Long.MAX_VALUE / 2) != Cfg.version) {
            Store.deletePrefix("streams2:")
            Store.put("sys:version", Cfg.version)
        }
    }
    HttpApi.start()
    Repos.startBackgroundSync()
    Thread {
        runCatching { Formatter.warmup() }
            .onFailure { AppLogger.e("formatter warmup failed: ${it.message}") }
    }.apply { isDaemon = true; name = "formatter-warmup" }.start()
    Thread {
        while (true) {
            try { Thread.sleep(24L * 3600 * 1000) } catch (_: InterruptedException) {}
            runCatching { Store.cleanup() }
        }
    }.apply { isDaemon = true; name = "kv-cleanup" }.start()
    AppLogger.i("CloudStream Bridge ready")
}
