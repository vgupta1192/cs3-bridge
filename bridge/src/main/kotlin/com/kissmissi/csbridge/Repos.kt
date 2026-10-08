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
    /** In memory (classes loaded, providers registered). */
    @Volatile var loaded: Boolean = false
    var error: String? = null
    var providerNames: List<String> = emptyList()
    var failedAt: Long = 0
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

    // Plugins are loaded lazily: only those enabled in an install (or requested
    // by a stream/catalog call) are brought into memory. Loading all ~450 repo
    // plugins kept every plugin's classes, static state and init-time network
    // jobs (live-TV plugins poll Firebase etc.) resident for nothing.
    // CSBRIDGE_LOAD_ALL=1 restores the old load-everything behaviour.
    private val loadAll = System.getenv("CSBRIDGE_LOAD_ALL") == "1"
    private val loadLock = Any()
    private const val LOAD_RETRY_MS = 30L * 60 * 1000

    // ---- persisted plugin index (data/plugins-index.json) ----
    // Remembers what is on disk so a restart does not re-download every .cs3
    // (the in-memory map is empty at boot, which used to force a full
    // re-download + reload of all plugins on every start).
    data class IndexEntry(
        val version: Int = 0,
        val url: String = "",
        val repo: String = "",
        val name: String = "",
        val description: String? = null,
        val iconUrl: String? = null,
        val language: String? = null,
        val tvTypes: List<String>? = null,
    )

    private val indexFile get() = File(Cfg.dataDir, "plugins-index.json")
    private val index = ConcurrentHashMap<String, IndexEntry>()

    private fun loadIndex() {
        runCatching { mapper.readValue<Map<String, IndexEntry>>(indexFile) }
            .getOrDefault(emptyMap())
            .forEach { (k, v) -> index[k] = v }
    }

    private fun saveIndex() {
        runCatching {
            val tmp = File(Cfg.dataDir, "plugins-index.json.tmp")
            tmp.writeText(mapper.writeValueAsString(index.toSortedMap()))
            if (!tmp.renameTo(indexFile)) { indexFile.delete(); tmp.renameTo(indexFile) }
        }.onFailure { AppLogger.e("RepoSync: could not save plugin index: ${it.message}") }
    }

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

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val req = okhttp3.Request.Builder().url(url).build()
        app.baseClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            resp.body?.string() ?: throw RuntimeException("empty body")
        }
    }

    /** Repo URLs may point at a raw plugin array or a repository.json wrapper with pluginLists. */
    private suspend fun fetchRepoPlugins(url: String): List<RawPlugin> {
        val body = fetchText(url)
        val node = runCatching { mapper.readTree(body) }.getOrNull()
            ?: throw RuntimeException("not JSON (is this a GitHub page instead of the repo.json link?)")
        return if (node.isArray) {
            mapper.readValue(body)
        } else {
            val lists = node.path("pluginLists")
            if (lists.isArray && lists.size() > 0) {
                lists.map { it.asText() }.flatMap { sub ->
                    runCatching { fetchRepoPlugins(normalizeUrl(sub)) }.getOrElse {
                        AppLogger.e("RepoSync: sub-list failed $sub: ${it.message}")
                        emptyList()
                    }
                }
            } else emptyList()
        }
    }

    // ---- repo URL resolution ----

    /** cloudstreamrepo:// links and github blob links → plain fetchable https URLs. */
    private fun normalizeUrl(raw: String): String {
        var u = raw.trim()
        if (u.startsWith("cloudstreamrepo://")) u = "https://" + u.removePrefix("cloudstreamrepo://")
        // github.com/<o>/<r>/blob/<branch>/<path> → raw.githubusercontent.com/<o>/<r>/<branch>/<path>
        Regex("""^https?://github\.com/([^/]+)/([^/]+)/(?:blob|raw)/(.+)$""").find(u)?.let {
            val (o, r, rest) = it.destructured
            u = "https://raw.githubusercontent.com/$o/$r/$rest"
        }
        return u.trimEnd('/')
    }

    /**
     * Turn whatever the user pasted into a URL that returns repo JSON. A bare
     * GitHub repository page (github.com/<owner>/<repo>, optionally /tree/<branch>)
     * is HTML, which the sync could never parse — probe the usual locations of
     * repo.json / plugins.json on raw.githubusercontent.com instead.
     */
    private suspend fun resolveRepoUrl(input: String): String? {
        val u = normalizeUrl(input)
        val gh = Regex("""^https?://github\.com/([^/]+)/([^/?#]+)(?:/tree/([^?#]+))?/?$""").find(u)
        if (gh == null) {
            return if (runCatching { fetchRepoPlugins(u).isNotEmpty() }.getOrDefault(false)) u else null
        }
        val (owner, repoRaw, branchRaw) = gh.destructured
        val repo = repoRaw.removeSuffix(".git")
        val branches = LinkedHashSet<String>()
        if (branchRaw.isNotBlank()) branches.add(branchRaw.trimEnd('/'))
        runCatching {
            mapper.readTree(fetchText("https://api.github.com/repos/$owner/$repo")).path("default_branch").asText("")
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { branches.add(it) }
        branches.addAll(listOf("main", "master", "builds"))
        val files = listOf("repo.json", "plugins.json", "builds/repo.json", "builds/plugins.json")
        for (b in branches) for (f in files) {
            val candidate = "https://raw.githubusercontent.com/$owner/$repo/$b/$f"
            if (runCatching { fetchRepoPlugins(candidate).isNotEmpty() }.getOrDefault(false)) return candidate
        }
        return null
    }

    private suspend fun repoDisplayName(url: String): String? = runCatching {
        mapper.readTree(fetchText(url)).path("name").asText("").takeIf { it.isNotBlank() }
    }.getOrNull()

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

    /**
     * Add a repository, resolve it to its JSON URL, sync it in the background
     * and (when [enableInInstalls]) switch its stream sources on in every saved
     * install, so new repos feed the existing Nuvio/Stremio installs without a
     * trip to the configure page.
     */
    fun addRepo(name: String, url: String, enableInInstalls: Boolean = true): Pair<Boolean, String> {
        if (!url.trim().startsWith("http") && !url.trim().startsWith("cloudstreamrepo://")) return false to "URL must start with http(s)"
        val resolved = runCatching { runBlocking { withTimeout(45000) { resolveRepoUrl(url) } } }.getOrNull()
            ?: return false to "Could not find a CloudStream repo.json/plugins.json at that URL. Paste the raw repo.json link (e.g. https://raw.githubusercontent.com/<owner>/<repo>/main/repo.json)."
        val repos = loadRepos()
        if (repos.any { normalizeUrl(it.url) == resolved || normalizeUrl(it.url) == normalizeUrl(url) }) return false to "Repository already added"
        val displayName = name.trim().ifBlank {
            runCatching { runBlocking { withTimeout(10000) { repoDisplayName(resolved) } } }.getOrNull()
                ?: resolved.substringAfter("githubusercontent.com/").substringBefore('/').ifBlank { resolved.substringAfterLast('/') }
        }
        if (repos.any { it.name == displayName }) return false to "A repository named \"$displayName\" already exists — give this one a different name"
        saveRepos(repos + Cfg.Repo(displayName, resolved))
        startSyncAsync(if (enableInInstalls) displayName else null)
        return true to "Added $displayName ($resolved). Syncing its plugins now${if (enableInInstalls) "; its sources will be switched on in your installs" else ""}."
    }

    fun removeRepo(url: String): Pair<Boolean, String> {
        val repos = loadRepos()
        val target = normalizeUrl(url)
        val removed = repos.filter { normalizeUrl(it.url) == target }
        if (removed.isEmpty()) return false to "Repository not found"
        saveRepos(repos - removed.toSet())
        // drop its plugins from memory and from the index
        for (r in removed) {
            plugins.values.filter { it.repo == r.name }.forEach { info ->
                if (info.loaded) runCatching { ExtensionLoader.unloadPlugin(info.file.absolutePath) }
                plugins.remove(info.internalName)
                index.remove(info.internalName)
            }
        }
        saveIndex()
        return true to "Removed."
    }

    /** One-off repair of repos saved before URL resolution existed (e.g. a bare github.com page). */
    private suspend fun repairRepoUrls() {
        val repos = loadRepos()
        var changed = false
        val fixed = repos.map { r ->
            val n = normalizeUrl(r.url)
            val needsResolve = Regex("""^https?://github\.com/[^/]+/[^/]+(/tree/.+)?/?$""").matches(n)
            when {
                needsResolve -> resolveRepoUrl(n)?.let {
                    AppLogger.i("RepoSync: resolved ${r.name}: ${r.url} -> $it"); changed = true; r.copy(url = it)
                } ?: r
                n != r.url -> { changed = true; r.copy(url = n) }
                else -> r
            }
        }
        if (changed) saveRepos(fixed)
    }

    // ---- loading ----

    fun providersOf(info: PluginInfo): List<MainAPI> =
        synchronized(APIHolder.allProviders) { APIHolder.allProviders.filter { it.sourcePlugin == info.file.absolutePath } }

    /** Bring one plugin into memory; false if it fails (retried after LOAD_RETRY_MS). */
    private fun ensureLoaded(info: PluginInfo): Boolean {
        if (info.loaded) return true
        if (info.error != null && System.currentTimeMillis() - info.failedAt < LOAD_RETRY_MS) return false
        synchronized(loadLock) {
            if (info.loaded) return true
            if (!info.file.exists()) { info.error = "not downloaded"; info.failedAt = System.currentTimeMillis(); return false }
            return try {
                ExtensionLoader.loadAndInit(info.file, forceBypassSecurity = true)
                // settings/utility plugins (SubscriptionManager, M3UPlaylistPlayer, ...)
                // load fine but register no stream providers — count them as loaded
                info.providerNames = providersOf(info).map { it.name }
                info.error = null
                info.loaded = true
                true
            } catch (t: Throwable) {
                var c: Throwable? = t; var chain = ""
                while (c != null) { chain += " <- " + c::class.java.simpleName + ": " + (c.message ?: ""); c = c.cause }
                info.error = (t.toString() + chain).take(220)
                info.failedAt = System.currentTimeMillis()
                AppLogger.e("RepoSync: failed to load ${info.internalName}: ${info.error}")
                false
            }
        }
    }

    private fun unload(info: PluginInfo) {
        if (!info.loaded) return
        synchronized(loadLock) {
            runCatching { ExtensionLoader.unloadPlugin(info.file.absolutePath) }
            info.loaded = false
        }
    }

    /** Names enabled in any saved install — loaded eagerly after a sync so the first tap is fast. */
    private fun wantedNames(): Set<String> = Installs.all().flatMap { cfg ->
        (cfg["p"] as? Map<*, *>)?.filterValues { (it as? Number)?.toInt() != 0 }?.keys?.map { it.toString() } ?: emptyList()
    }.toSet()

    fun enabledProviders(enabled: Set<String>): List<Pair<PluginInfo, List<MainAPI>>> =
        enabled.mapNotNull { plugins[it] }
            .filter { ensureLoaded(it) }
            .map { it to providersOf(it) }
            .filter { it.second.isNotEmpty() }

    fun isFailed(info: PluginInfo) = !info.loaded && info.error != null

    /** Load newly enabled plugins off the request path (configure-page saves). */
    fun preloadAsync(names: Collection<String>) {
        val todo = names.mapNotNull { plugins[it] }.filter { !it.loaded }
        if (todo.isEmpty()) return
        Thread {
            todo.forEach { runCatching { ensureLoaded(it) } }
        }.apply { isDaemon = true; name = "plugin-preload" }.start()
    }

    // ---- sync ----

    private fun startSyncAsync(enableRepo: String?) {
        Thread {
            try {
                runBlocking {
                    // another sync may be running (boot/periodic): wait it out so the new repo is not skipped
                    var waited = 0
                    while (isSyncing() && waited < 900) { Thread.sleep(1000); waited++ }
                    sync()
                }
                if (enableRepo != null) enableRepoInInstalls(enableRepo)
            } catch (t: Throwable) { AppLogger.e("repo add sync failed", t) }
        }.apply { isDaemon = true; name = "repo-add-sync" }.start()
    }

    /** Switch on every stream source of [repoName] in every saved install (other settings untouched). */
    private fun enableRepoInInstalls(repoName: String) {
        val names = plugins.values.filter { it.repo == repoName && ensureLoaded(it) && it.providerNames.isNotEmpty() }
            .map { it.internalName }
        if (names.isEmpty()) return
        val n = Installs.enableEverywhere(names)
        AppLogger.i("RepoSync: enabled ${names.size} sources of $repoName in $n installs")
    }

    suspend fun sync(force: Boolean = false): String {
        if (!syncing.compareAndSet(false, true)) return "sync already running"
        try {
            Cfg.extensionsDir.mkdirs()
            if (index.isEmpty()) loadIndex()
            runCatching { repairRepoUrls() }
            var downloaded = 0; var updated = 0; var failed = 0
            val seen = HashMap<String, String>() // internalName -> repo that owns it this run
            val wanted = wantedNames()
            for (repo in loadRepos()) {
                val entries: List<RawPlugin> = try { fetchRepoPlugins(repo.url) } catch (e: Exception) {
                    AppLogger.e("RepoSync: failed to fetch/parse ${repo.name}: ${e.message}")
                    failed++
                    // keep serving what is already on disk for this repo
                    index.filterValues { it.repo == repo.name }.keys.forEach { seen.putIfAbsent(it, repo.name) }
                    continue
                }
                for (raw in entries) {
                    if (raw.url.isBlank() || raw.internalName.isBlank()) continue
                    // two repos shipping the same internalName used to overwrite each
                    // other's file and flip-flop versions on every sync: first repo wins
                    seen[raw.internalName]?.let { owner ->
                        if (owner != repo.name) AppLogger.i("RepoSync: ${raw.internalName} from ${repo.name} ignored (already provided by $owner)")
                        continue
                    }
                    seen[raw.internalName] = repo.name
                    val file = File(Cfg.extensionsDir, "${raw.internalName}.cs3")
                    val prev = index[raw.internalName]
                    val existing = plugins[raw.internalName]
                    val stale = force || !file.exists() || prev == null || prev.version != raw.version || prev.url != raw.url
                    try {
                        if (stale) {
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
                            if (prev != null) updated++
                        }
                        index[raw.internalName] = IndexEntry(
                            raw.version, raw.url, repo.name, raw.name, raw.description, raw.iconUrl, raw.language, raw.tvTypes,
                        )
                        if (existing != null && !stale && existing.repo == repo.name) continue
                        val wasLoaded = existing?.loaded == true
                        if (existing != null) unload(existing)
                        val info = PluginInfo(
                            internalName = raw.internalName, name = raw.name.ifBlank { raw.internalName },
                            description = raw.description, version = raw.version, iconUrl = raw.iconUrl,
                            language = raw.language, tvTypes = raw.tvTypes ?: emptyList(),
                            repo = repo.name, file = file,
                        )
                        plugins[raw.internalName] = info
                        if (loadAll || wasLoaded || raw.internalName in wanted) {
                            if (!ensureLoaded(info)) failed++
                        }
                    } catch (t: Throwable) {
                        failed++
                        AppLogger.e("RepoSync: failed on ${raw.internalName}: ${t.message}")
                    }
                }
            }
            // drop plugins whose repo no longer lists them
            for (name in plugins.keys.toList()) {
                if (name !in seen) {
                    plugins[name]?.let { unload(it) }
                    plugins.remove(name)
                    index.remove(name)
                }
            }
            saveIndex()
            lastSync = System.currentTimeMillis()
            val active = plugins.values.count { it.loaded }
            val msg = "RepoSync done: ${plugins.size} known, $active in memory, $downloaded downloaded, $updated updated, $failed failed"
            AppLogger.i(msg)
            return msg
        } finally {
            syncing.set(false)
        }
    }

    /** Boot: register everything already on disk (no network) and load the wanted set. */
    private fun bootFromIndex() {
        loadIndex()
        val wanted = wantedNames()
        for ((name, e) in index) {
            val file = File(Cfg.extensionsDir, "$name.cs3")
            if (!file.exists()) continue
            val info = PluginInfo(name, e.name.ifBlank { name }, e.description, e.version, e.iconUrl, e.language,
                e.tvTypes ?: emptyList(), e.repo, file)
            plugins[name] = info
            // a few plugins import classes from ANOTHER plugin's jar (AnimeWorld
            // uses doGior's it.dogior.hadEnough.*); with lazy loading that jar may
            // not be loaded, so publish every converted jar to the shared loader
            // up front (URL only — no classes are loaded until asked for)
            File(Cfg.extensionsDir, "$name-jvm.jar").takeIf { it.exists() }
                ?.let { com.lagradost.runtime.loader.SafePluginClassLoader.addToSharedDependencies(it) }
        }
        for (info in plugins.values) if (loadAll || info.internalName in wanted) ensureLoaded(info)
        AppLogger.i("RepoSync: boot from index: ${plugins.size} known, ${plugins.values.count { it.loaded }} in memory")
    }

    fun isSyncing() = syncing.get()

    fun lastSyncAt(): Long = lastSync

    fun startBackgroundSync() {
        Thread {
            try { bootFromIndex() } catch (t: Throwable) { AppLogger.e("boot from index failed", t) }
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

    /** One provider probe: a common search on each of the plugin's providers, 14 s cap. */
    private suspend fun probe(info: PluginInfo): HealthEntry {
        val t0 = System.currentTimeMillis()
        val entry = HealthEntry(status = "down", lastOk = healthOf(info.internalName).lastOk, lastCheck = System.currentTimeMillis())
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
        return entry
    }

    /** Health check for ONE plugin (loads it if needed); result stored like the full run's. */
    fun checkOne(internalName: String): Pair<PluginInfo, HealthEntry>? {
        loadHealth()
        val info = plugins[internalName] ?: return null
        if (!ensureLoaded(info)) {
            val e = HealthEntry(status = "down", lastOk = healthOf(internalName).lastOk, lastCheck = System.currentTimeMillis())
            health[internalName] = e; persistHealth()
            return info to e
        }
        val e = runBlocking { probe(info) }
        health[internalName] = e
        persistHealth()
        AppLogger.i("Health: ${info.internalName} ${e.status} in ${e.ms} ms")
        return info to e
    }

    /** Probe every in-memory plugin with a common search; async, results land in health.json. */
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
                                health[info.internalName] = probe(info)
                            } finally {
                                sem.release()
                            }
                        }
                    }
                }
                persistHealth()
            } catch (t: Throwable) {
                AppLogger.e("health check run failed", t)
            } finally {
                healthRunning.set(false)
            }
        }.apply { isDaemon = true; name = "health-check" }.start()
        return true
    }
}
