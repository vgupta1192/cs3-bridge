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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

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

data class HealthEntry(var status: String = "unchecked", var lastOk: Long = 0, var lastCheck: Long = 0, var ms: Long = 0)

object Repos {
    private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val syncing = AtomicBoolean(false)
    private var lastSync: Long = 0
    private val health = ConcurrentHashMap<String, HealthEntry>()
    private val healthRunning = AtomicBoolean(false)
    private val healthFile get() = File(Cfg.dataDir, "health.json")

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

    /** Repo URLs may point at a raw plugin array or a repository.json wrapper with pluginLists. */
    private suspend fun fetchRepoPlugins(url: String): List<RawPlugin> {
        val body = withContext(Dispatchers.IO) {
            val req = okhttp3.Request.Builder().url(url).build()
            app.baseClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                resp.body?.string() ?: throw RuntimeException("empty body")
            }
        }
        val node = mapper.readTree(body)
        return if (node.isArray) {
            mapper.readValue(body)
        } else {
            val lists = node.path("pluginLists")
            if (lists.isArray && lists.size() > 0) {
                lists.map { it.asText() }.flatMap { sub ->
                    runCatching { fetchRepoPlugins(sub) }.getOrElse {
                        AppLogger.e("RepoSync: sub-list failed $sub: ${it.message}")
                        emptyList()
                    }
                }
            } else emptyList()
        }
    }

    // ---- repo list management (data/repos.json, seeded with Cfg.DEFAULT_REPOS) ----
    private val reposLock = Any()

    fun loadRepos(): List<Cfg.Repo> = synchronized(reposLock) {
        if (!Cfg.reposFile.exists()) return Cfg.DEFAULT_REPOS
        runCatching { mapper.readValue<List<Cfg.Repo>>(Cfg.reposFile) }.getOrDefault(Cfg.DEFAULT_REPOS)
    }

    private fun saveRepos(list: List<Cfg.Repo>) = synchronized(reposLock) {
        Cfg.dataDir.mkdirs()
        Cfg.reposFile.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(list))
    }

    fun addRepo(name: String, url: String): Pair<Boolean, String> {
        val u = url.trim().trimEnd('/')
        if (!u.startsWith("http")) return false to "URL must start with http(s)"
        val repos = loadRepos()
        if (repos.any { it.url.trimEnd('/') == u }) return false to "Repository already added"
        val displayName = name.trim().ifBlank { u.substringAfterLast('/').removeSuffix(".json") }
        val ok = runCatching { runBlocking { withTimeout(15000) { Resolver.httpGet(u) != null } } }.getOrDefault(false)
        saveRepos(repos + Cfg.Repo(displayName, u))
        if (!ok) return true to "Added (warning: could not fetch plugins.json right now - it will be retried on sync)"
        return true to "Added. Run Resync to load its plugins."
    }

    fun removeRepo(url: String): Pair<Boolean, String> {
        val repos = loadRepos()
        val remaining = repos.filter { it.url.trimEnd('/') != url.trim().trimEnd('/') }
        if (remaining.size == repos.size) return false to "Repository not found"
        saveRepos(remaining)
        return true to "Removed. Run Resync to drop its plugins."
    }


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
            for (repo in loadRepos()) {
                val entries: List<RawPlugin> = try { fetchRepoPlugins(repo.url) } catch (e: Exception) {
                    AppLogger.e("RepoSync: failed to fetch/parse ${repo.name}: ${e.message}")
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
                            // settings/utility plugins (SubscriptionManager,
                            // M3UPlaylistPlayer, ...) load fine but register no
                            // stream providers — count them as loaded
                            info.loaded = true
                            info.providerNames = provs.map { it.name }
                            loadedNow++
                        } catch (t: Throwable) {
                            info.loaded = false
                            var c: Throwable? = t; var chain = ""
                            while (c != null) { chain += " <- " + c::class.java.simpleName + ": " + (c.message ?: ""); c = c.cause }
                            info.error = (t.toString() + chain).take(220)
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

    // ---- health checks ----
    private fun loadHealth() {
        if (health.isNotEmpty()) return
        runCatching { mapper.readValue<Map<String, HealthEntry>>(healthFile) }
            .getOrDefault(emptyMap())
            .forEach { (k, v) -> health[k] = v }
    }

    private fun persistHealth() {
        runCatching { healthFile.writeText(mapper.writeValueAsString(health)) }
    }

    fun healthOf(internalName: String): HealthEntry {
        loadHealth()
        return health[internalName] ?: HealthEntry()
    }

    fun isHealthRunning() = healthRunning.get()

    /** Probe every loaded plugin with a common search; async, results land in health.json. */
    fun startHealthCheck(): Boolean {
        loadHealth()
        if (!healthRunning.compareAndSet(false, true)) return false
        Thread {
            try {
                runBlocking {
                    val sem = Semaphore(6)
                    for ((_, info) in plugins.toList().sortedBy { it.second.internalName }) {
                        if (!info.loaded) continue
                        launch {
                            sem.acquire()
                            try {
                                val t0 = System.currentTimeMillis()
                                val entry = HealthEntry(status = "down", lastCheck = System.currentTimeMillis())
                                try {
                                    withTimeout(14000) {
                                        val provs = providersOf(info)
                                        val anyOk = provs.any { p ->
                                            runCatching { p.search("inception", 1); true }.getOrDefault(false)
                                        }
                                        if (anyOk) { entry.status = "up"; entry.lastOk = System.currentTimeMillis() }
                                    }
                                } catch (_: Exception) {}
                                entry.ms = System.currentTimeMillis() - t0
                                health[info.internalName] = entry
                                persistHealth()
                            } finally {
                                sem.release()
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.e("health check run failed", t)
            } finally {
                healthRunning.set(false)
            }
        }.apply { isDaemon = true; name = "health-check" }.start()
        return true
    }
}
