package com.kissmissi.csbridge

import java.io.File

object Cfg {
    val port: Int = System.getenv("CSBRIDGE_PORT")?.toIntOrNull() ?: 7095
    val dataDir: File = File(System.getenv("CSBRIDGE_DATA_DIR") ?: "./data")
    val extensionsDir: File get() = File(dataDir, "extensions")
    val deadlineMs: Long = System.getenv("CSBRIDGE_DEADLINE_MS")?.toLongOrNull() ?: 28000L
    val providerTimeoutMs: Long = System.getenv("CSBRIDGE_PROVIDER_TIMEOUT_MS")?.toLongOrNull() ?: 18000L
    val maxConcurrent: Int = System.getenv("CSBRIDGE_MAX_CONCURRENT")?.toIntOrNull() ?: 12
    val tmdbKey: String? = System.getenv("TMDB_API_KEY")?.takeIf { it.isNotBlank() }
    val version: String = System.getenv("CSBRIDGE_VERSION") ?: "1.0.0"

    val repos: List<Repo> = run {
        val env = System.getenv("CSBRIDGE_REPOS")
        if (!env.isNullOrBlank()) {
            runCatching {
                val arr = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(env)
                (0 until arr.size()).map { i ->
                    Repo(arr.get(i).path("name").asText("repo"), arr.get(i).path("url").asText())
                }.filter { it.pluginsUrl.isNotBlank() }
            }.getOrDefault(DEFAULT_REPOS)
        } else DEFAULT_REPOS
    }

    const val ADDON_ID = "com.kissmissi.csbridge"
    const val ADDON_NAME = "CloudStream Bridge"

    data class Repo(val name: String, val pluginsUrl: String)

    val DEFAULT_REPOS = listOf(
        Repo("Phisher", "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/plugins.json"),
        Repo("CSX", "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/plugins.json"),
        Repo("Raghav", "https://raw.githubusercontent.com/KSHITIJ8473/raghav/builds/plugins.json"),
    )
}
