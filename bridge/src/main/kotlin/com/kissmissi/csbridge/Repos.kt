@file:OptIn(com.lagradost.cloudstream3.Prerelease::class)

package com.kissmissi.csbridge

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.runtime.loader.ExtensionLoader
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class PluginInfo(
    val internalName: String,
    val name: String,
    val description: String?,
    val version: Int,
    val iconUrl: String?,
    val language: String?,
    val tvTypes: List<String>,
    val repo: String,
    val file: File,
) {
    var loaded: Boolean = false
    var error: String? = null
    var providerNames: List<String> = emptyList()
}

object Repos {
    private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val syncing = AtomicBoolean(false)
    private var lastSync: Long = 0

    // internalName -> plugin info + state
    val plugins = java.util.concurrent.ConcurrentHashMap<String, PluginInfo>()


    data class RawPlugin(
        val url: String = "",
        val status: Int = 1,
        val version: Int = 1,
        val name: String = "",
        val internalName: String = "",
        val description: String? = null,
        val language: String? = null,
        val tvTypes: List<String>? = null,
        val iconUrl: String? = null,
        val apiVersion: Int = 1,
    )


    fun providersOf(info: PluginInfo): List<MainAPI> =
        APIHolder.allProviders.filter { it.sourcePlugin == info.file.absolutePath }

    fun enabledProviders(enabled: Set<String>): List<Pair<PluginInfo, List<MainAPI>>> =
        plugins.values.filter { it.loaded && enabled.contains(it.internalName) }
            .map { it to providersOf(it) }
            .filter { it.second.isNotEmpty() }


    suspend fun sync(force: Boolean = false): String {
        if (!syncing.compareAndSet(false, true)) return "sync already running"
        try {
            Cfg.extensionsDir.mkdirs()
            var downloaded = 0; var updated = 0; var failed = 0; var loadedNow = 0
            for (repo in Cfg.repos) {
                val json = try {
                    withContext(Dispatchers.IO) {
                        val req = okhttp3.Request.Builder().url(repo.pluginsUrl).build()
                        app.baseClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                            resp.body?.string() ?: throw RuntimeException("empty body")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("RepoSync: failed to fetch ${repo.name}: ${e.message}")
                    failed++
                    continue
                }
                val entries: List<RawPlugin> = try { mapper.readValue(json) } catch (e: Exception) {
                    AppLogger.e("RepoSync: failed to parse ${repo.name}: ${e.message}")
                    failed++
                    continue
                }
                for (raw in entries) {
                    if (raw.url.isBlank() || raw.internalName.isBlank()) continue
                    val existing = plugins[raw.internalName]
                    if (existing != null && existing.version == raw.version && existing.loaded && !force) continue
                    val file = File(Cfg.extensionsDir, "${raw.internalName}.cs3")
                    try {
                        if (!file.exists() || existing == null || existing.version != raw.version) {
                            withContext(Dispatchers.IO) {
                                val req = okhttp3.Request.Builder().url(raw.url).build()
                                app.baseClient.newCall(req).execute().use { resp ->
                                    if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                                    val tmp = File(Cfg.extensionsDir, "${raw.internalName}.cs3.tmp")
                                    tmp.outputStream().use { out -> (resp.body ?: throw RuntimeException("empty body")).byteStream().copyTo(out) }
                                    if (!tmp.renameTo(file)) {
                                        file.delete(); tmp.renameTo(file)
                                    }
                                }
                            }
                            downloaded++
                        }
                        if (existing != null && existing.loaded && existing.version != raw.version) {
                            runCatching { ExtensionLoader.unloadPlugin(existing.file.absolutePath) }
                            updated++
                        }
                        val info = PluginInfo(
                            internalName = raw.internalName, name = raw.name.ifBlank { raw.internalName },
                            description = raw.description, version = raw.version, iconUrl = raw.iconUrl,
                            language = raw.language, tvTypes = raw.tvTypes ?: emptyList(),
                            repo = repo.name, file = file,
                        )
                        try {
                            ExtensionLoader.loadAndInit(file, forceBypassSecurity = true)
                            val provs = providersOf(info)
                            if (provs.isEmpty()) throw RuntimeException("no providers registered")
                            info.loaded = true
                            info.providerNames = provs.map { it.name }
                            loadedNow++
                        } catch (t: Throwable) {
                            info.loaded = false
                            info.error = t.message ?: t.toString()
                            AppLogger.e("RepoSync: failed to load ${raw.internalName}: ${info.error}")
                            failed++
                        }
                        plugins[raw.internalName] = info
                    } catch (t: Throwable) {
                        failed++
                        AppLogger.e("RepoSync: failed on ${raw.internalName}: ${t.message}")
                    }
                }
            }
            lastSync = System.currentTimeMillis()
            val msg = "RepoSync done: ${plugins.size} known, $loadedNow loaded, $downloaded downloaded, $updated updated, $failed failed"
            AppLogger.i(msg)
            return msg
        } finally {
            syncing.set(false)
        }
    }

    fun isSyncing() = syncing.get()

    fun lastSyncAt(): Long = lastSync

    fun startBackgroundSync() {
        Thread {
            try { runBlocking { sync() } } catch (t: Throwable) { AppLogger.e("startup sync failed", t) }
            while (true) {
                try { Thread.sleep(6L * 3600 * 1000); runBlocking { sync() } } catch (t: Throwable) { AppLogger.e("periodic sync failed", t) }
            }
        }.apply { isDaemon = true; name = "repo-sync" }.start()
    }
}
