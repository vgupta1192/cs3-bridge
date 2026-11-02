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
    checkDataDirWritable()
    Boot.init()
    // cached stream lists embed the formatter output — flush them whenever the
    // deployed version changes so old formatting never lingers past a deploy
    runCatching {
        if (Store.get("sys:version", Long.MAX_VALUE / 2) != Cfg.version) {
            Store.deletePrefix("streams2:")
            Store.put("sys:version", Cfg.version)
        }
    }
    Admin.init()
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

/**
 * The image runs as uid 10001. A data dir created by an older root-run
 * container (bind mount) is root-owned and unwritable: say how to fix it
 * instead of failing later inside SQLite or the plugin sync.
 */
private fun checkDataDirWritable() {
    val dir = Cfg.dataDir
    val probe = java.io.File(dir, ".write-test")
    val ok = runCatching {
        dir.mkdirs()
        probe.writeText("ok")
        // files written by the old root container are what actually break
        dir.walkTopDown().maxDepth(2).all { it.canWrite() }
    }.getOrDefault(false)
    runCatching { probe.delete() }
    if (!ok) {
        AppLogger.e(
            "Data dir ${dir.absolutePath} (or files in it) is not writable by user ${System.getProperty("user.name")}. " +
                "If it was created by an older root-run container, run once on the host: " +
                "chown -R 10001:10001 <host path of the data dir>"
        )
        kotlin.system.exitProcess(1)
    }
}
