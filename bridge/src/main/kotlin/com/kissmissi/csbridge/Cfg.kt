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

    const val ADDON_ID = "com.kissmissi.csbridge"
    const val ADDON_NAME = "CloudStream Bridge"

    data class Repo(val name: String, val url: String, val iconUrl: String? = null, val description: String? = null)

    // The 16 repos CNCVerse ships, used as the default catalog of repositories.
    val DEFAULT_REPOS = listOf(
        Repo("CNC Repo (All Language)", "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/CNC.json",
            "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/cnc.png", "All Language Contents"),
        Repo("Phisher Repo", "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json",
            null, "Hindi, English, and other languages of India content."),
        Repo("Megix Repo (Hindi & English)", "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json",
            null, "Hindi and English"),
        Repo("raghav repo", "https://raw.githubusercontent.com/KSHITIJ8473/raghav/builds/repo.json",
            null, "Raghav CloudStream extensions for anime, movies and shows."),
        Repo("3rabi", "https://raw.githubusercontent.com/Abodabodd/re-3arabi/refs/heads/main/repo",
            null, "Arabic CloudStream extensions."),
        Repo("Storm-ext Fork", "https://raw.githubusercontent.com/redblacker8/storm-ext/refs/heads/builds/repo.json",
            null, "Storm extensions fork by redblacker8."),
        Repo("cs-karma", "https://raw.githubusercontent.com/Kraptor123/cs-Karma/refs/heads/master/repo.json",
            null, "Karma CloudStream extensions."),
        Repo("TurkSinema", "https://raw.githubusercontent.com/Wiojelt/TurkSinema/main/repo.json",
            null, "Turkish cinema and series providers."),
        Repo("doGior's Had Enough", "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/builds/repo.json",
            null, "doGior's CloudStream extensions."),
        Repo("DieGon Repository", "https://pastebin.com/raw/qndZtL6D",
            null, "DieGon CloudStream repository."),
        Repo("French providers repository", "https://raw.githubusercontent.com/yorik100/Cloudstream/refs/heads/builds/repo.json",
            null, "French CloudStream providers."),
        Repo("Cloudstream providers repository", "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
            null, "The official recloudstream extensions repository."),
        Repo("German providers repository", "https://raw.githubusercontent.com/Bnyro/GermanProviders/refs/heads/master/repo.json",
            null, "German CloudStream providers."),
        Repo("CakesTwix Providers Repository", "https://raw.githubusercontent.com/CakesTwix/cloudstream-extensions-uk/master/repo.json",
            null, "Ukrainian CloudStream extensions by CakesTwix."),
        Repo("Redowan's BDIX repository", "https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json",
            null, "BDIX (Bangladesh) CloudStream providers."),
        Repo("lietrepo", "https://raw.githubusercontent.com/lawlietbr/lietrepo/refs/heads/main/builds/repo.json",
            null, "Liet CloudStream repository."),
    )

    val reposFile: File get() = File(dataDir, "repos.json")
}
