package com.kissmissi.csbridge

import com.lagradost.common.logging.AppLogger

/** stdout with each line cut at [max] bytes: plugins println whole pages. */
private class LineCapStream(private val out: java.io.OutputStream, private val max: Int) : java.io.OutputStream() {
    private var col = 0
    override fun write(b: Int) {
        if (b == '\n'.code) { col = 0; out.write(b); return }
        col++
        if (col <= max) out.write(b) else if (col == max + 1) out.write("…".toByteArray())
    }
    override fun flush() = out.flush()
}

fun main() {
    (System.getenv("CSBRIDGE_STDOUT_LINE_MAX")?.toIntOrNull() ?: 400).takeIf { it > 0 }?.let { max ->
        val fd = java.io.BufferedOutputStream(java.io.FileOutputStream(java.io.FileDescriptor.out), 8192)
        System.setOut(java.io.PrintStream(LineCapStream(fd, max), true, "UTF-8"))
    }
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
    Warmer.start()
    Thread {
        runCatching { Formatter.warmup() }
            .onFailure { AppLogger.e("formatter warmup failed: ${it.message}") }
        val t0 = System.currentTimeMillis()
        runCatching { Formatter.warmInstalls() }
            .onFailure { AppLogger.e("formatter install warmup failed: ${it.message}") }
        AppLogger.i("formatter warm in ${System.currentTimeMillis() - t0} ms")
    }.apply { isDaemon = true; name = "formatter-warmup" }.start()
    Thread {
        while (true) {
            try { Thread.sleep(24L * 3600 * 1000) } catch (_: InterruptedException) {}
            runCatching { Store.cleanup() }
        }
    }.apply { isDaemon = true; name = "kv-cleanup" }.start()
    AppLogger.i("CloudStream Bridge ready")
}
