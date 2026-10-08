package com.lagradost.runtime.loader

import android.content.DesktopContextProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.googlecode.dex2jar.tools.Dex2jarCmd
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.common.logging.AppLogger
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

object ExtensionLoader {

    private val mapper = ObjectMapper().registerModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // Keep track of loaded plugins by absolute path
    val plugins: MutableMap<String, BasePlugin> = mutableMapOf()

    // Map class loader to plugin name
    val classLoaders: MutableMap<ClassLoader, String> = java.util.concurrent.ConcurrentHashMap()

    fun getCallingPluginName(): String? {
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            val className = element.className
            if (className.startsWith("com.lagradost.") || className.startsWith("java.") || className.startsWith("kotlin.")) continue
            
            for ((loader, name) in classLoaders) {
                try {
                    val clazz = Class.forName(className, false, loader)
                    if (clazz.classLoader == loader) return name
                } catch (e: ClassNotFoundException) {}
            }
        }
        return null
    }

    // Native plugin interceptors
    var nativePluginInterceptor: ((String) -> BasePlugin?)? = null

    fun loadJar(jarFile: File, fallbackPluginClassName: String? = null, forceBypassSecurity: Boolean = false): BasePlugin {
        if (!jarFile.exists()) {
            throw IllegalArgumentException("Jar file does not exist: ${jarFile.absolutePath}")
        }

        var pluginClassName = fallbackPluginClassName
        var internalNameFromManifest: String? = null
        var nameFromManifest: String? = null
        var jarToLoad = jarFile

        ZipFile(jarFile).use { zip ->
            // Try to extract manifest to get actual class name
            val manifestEntry = zip.getEntry("manifest.json")
            if (manifestEntry != null) {
                zip.getInputStream(manifestEntry).use { input ->
                    val manifestData = mapper.readValue(input, Map::class.java)
                    val className = manifestData["pluginClassName"] as? String
                    if (className != null) {
                        pluginClassName = className
                    }
                    internalNameFromManifest = manifestData["internalName"] as? String
                    nameFromManifest = manifestData["name"] as? String
                }
            }

            // Check if it's Dalvik bytecode
            val dexEntry = zip.getEntry("classes.dex")
            if (dexEntry != null) {
                val dexFile = File(jarFile.parentFile, jarFile.nameWithoutExtension + ".dex")

                val convertedJar = File(jarFile.parentFile, jarFile.nameWithoutExtension + "-jvm.jar")
                // The converted jar is tied to the dex it came from: a sidecar holds
                // the dex CRC+size. Without it an updated .cs3 kept running the OLD
                // converted classes forever (the cache only checked existence).
                // "t2" = transformer revision; bump it when PluginBytecodeTransformer
                // changes so cached conversions are redone
                val dexTag = "t2:${dexEntry.crc}:${dexEntry.size}"
                val tagFile = File(jarFile.parentFile, jarFile.nameWithoutExtension + "-jvm.jar.dex")
                val tagOk = tagFile.exists() && runCatching { tagFile.readText().trim() == dexTag }.getOrDefault(false)
                // <64 B = empty artifact of an interrupted conversion (dex2jar
                // writes progressively; a killed run used to poison the cache
                // forever, e.g. AniSnatch/AniSuge shipped 22-byte jars)
                if (!convertedJar.exists() || convertedJar.length() < 64 || !tagOk) {
                    convertedJar.delete()
                    zip.getInputStream(dexEntry).use { input ->
                        Files.copy(input, dexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    AppLogger.i("Transpiling Dalvik classes.dex of ${jarFile.name} to JVM classes.jar...")
                    val tmpJar = File(jarFile.parentFile, jarFile.nameWithoutExtension + "-jvm.jar.tmp")
                    tmpJar.delete()
                    // Subprocess, not in-process: dex2jar has exponential-case
                    // transformers (Ir2JRegAssignTransformer / DexFix) that can
                    // grind ONE dex for hours, and huge dexes OOM'd the bridge
                    // JVM. 15-minute cap, then the plugin is marked failed.
                    convertDexInSubprocess(dexFile, tmpJar, jarFile.name)
                    if (!tmpJar.exists() || tmpJar.length() < 64) {
                        throw IllegalStateException("dex2jar produced no output for ${jarFile.name}")
                    }

                    // Structurally rewrite the JAR to fix dex2jar renaming Kotlin inline class methods
                    PluginBytecodeTransformer.transform(tmpJar)
                    if (!tmpJar.renameTo(convertedJar)) {
                        convertedJar.delete()
                        if (!tmpJar.renameTo(convertedJar)) throw IllegalStateException("could not finalize ${convertedJar.name}")
                    }
                    runCatching { tagFile.writeText(dexTag) }
                }
                runCatching { dexFile.delete() }

                jarToLoad = convertedJar
                dexFile.delete() // Cleanup
            }
        }

        if (pluginClassName == null) {
            throw IllegalArgumentException("Could not determine pluginClassName from manifest.json and no fallback provided.")
        }

        AppLogger.i("Loading plugin class: $pluginClassName from ${jarToLoad.absolutePath}")

        if (!forceBypassSecurity && !isTrusted(jarToLoad)) {
            AppLogger.i("Running static bytecode security verification on ${jarToLoad.name}...")
            com.lagradost.runtime.security.PluginSecurityVerifier.verifyJar(jarToLoad)
        } else {
            if (forceBypassSecurity) {
                addTrusted(jarToLoad)
            }
            AppLogger.i("Bypassing static bytecode security verification for trusted plugin ${jarToLoad.name}!")
        }

        val nativeIntercept = nativePluginInterceptor?.invoke(pluginClassName!!)
        var pluginInstance: BasePlugin? = null
        if (nativeIntercept != null) {
            AppLogger.i("Intercepted plugin $pluginClassName! Injecting native JVM implementation.")
            pluginInstance = nativeIntercept
        } else {
            var lastFailure: Throwable? = null
            // First attempt: isolated loader (parent-first over the bridge).
            // Retry once on linkage errors with the shared dependencies loader
            // as parent — some plugins import classes from ANOTHER repo's jar
            // (AnimeWorld imports it.dogior.hadEnough.* from doGior's jars).
            val attempts = listOf<ClassLoader?>(null, SafePluginClassLoader.sharedDependenciesLoader)
            for (shared in attempts) {
                try {
                    val classLoader = if (shared == null)
                        SafePluginClassLoader(jarToLoad.toURI().toURL(), this::class.java.classLoader)
                    else
                        SafePluginClassLoader(jarToLoad.toURI().toURL(), shared)
                    val pluginClass = classLoader.loadClass(pluginClassName)

                    // MegaPlugin VerifiedRepo MixIn injection
                    if (pluginClassName == "com.mega.MegaPlugin") {
                        try {
                            val verifiedRepoClass = classLoader.loadClass("com.mega.MegaPlugin\$getRepositories\$VerifiedRepo")
                            com.lagradost.cloudstream3.mapper.addMixIn(verifiedRepoClass, VerifiedRepoMixIn::class.java)
                        } catch (e: Exception) {
                            AppLogger.i("Failed to inject VerifiedRepo MixIn for MegaPlugin (it might not be loaded yet)")
                        }
                    }

                    val instance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
                    val finalInternalName = internalNameFromManifest ?: nameFromManifest ?: pluginClassName?.split(".")?.lastOrNull() ?: jarFile.nameWithoutExtension.removeSuffix("-jvm")
                    classLoaders[classLoader] = finalInternalName
                    pluginInstance = instance
                    // publish this jar so later plugins can resolve its classes
                    SafePluginClassLoader.addToSharedDependencies(jarToLoad)
                    break
                } catch (t: Throwable) {
                    if (t is LinkageError || t is ClassNotFoundException || t is ClassCastException) {
                        lastFailure = t
                        AppLogger.i("Load attempt failed for $pluginClassName (${t::class.java.simpleName}), ${if (shared == null) "retrying with shared deps" else "giving up"}")
                    } else {
                        throw t
                    }
                }
            }
            if (pluginInstance == null) {
                throw lastFailure ?: IllegalStateException("could not load $pluginClassName")
            }
        }

        pluginInstance.filename = jarFile.absolutePath
        // store plugin instance for later unloading
        plugins[jarFile.absolutePath] = pluginInstance

        return pluginInstance
    }

    /** dex2jar in a bounded subprocess; drains output so the pipe can't block. */
    private fun convertDexInSubprocess(dexFile: File, outJar: File, sourceName: String) {
        val cp = System.getProperty("java.class.path") ?: throw IllegalStateException("no classpath")
        val proc = ProcessBuilder(
            "java", "-Xmx1200m", "-cp", cp,
            "com.googlecode.dex2jar.tools.Dex2jarCmd",
            "-f", dexFile.absolutePath, "-o", outJar.absolutePath,
        ).redirectErrorStream(true).start()
        val drain = Thread {
            runCatching { proc.inputStream.use { it.readBytes() } }
        }.apply { isDaemon = true; name = "dex2jar-drain" }
        drain.start()
        val finished = proc.waitFor(15, java.util.concurrent.TimeUnit.MINUTES)
        if (!finished) {
            proc.destroyForcibly()
            throw IllegalStateException("dex2jar timed out after 15m on $sourceName")
        }
        if (proc.exitValue() != 0) {
            throw IllegalStateException("dex2jar failed rc=${proc.exitValue()} on $sourceName")
        }
    }

    private fun getTrustedList(): MutableList<String> {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val prefs = java.util.prefs.Preferences.userRoot().node("cloudstream_desktop_prefs")
        val json = prefs.get("trusted_plugins", "[]")
        return try {
            mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<MutableList<String>>() {})
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun isTrusted(jarFile: File): Boolean {
        val name = jarFile.nameWithoutExtension.removeSuffix("-jvm")
        return getTrustedList().contains(name)
    }

    private fun addTrusted(jarFile: File) {
        val name = jarFile.nameWithoutExtension.removeSuffix("-jvm")
        val trusted = getTrustedList()
        if (!trusted.contains(name)) {
            trusted.add(name)
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val prefs = java.util.prefs.Preferences.userRoot().node("cloudstream_desktop_prefs")
            prefs.put("trusted_plugins", mapper.writeValueAsString(trusted))
        }
    }

    fun loadAndInit(jarFile: File, fallbackPluginClassName: String? = null, forceBypassSecurity: Boolean = false): BasePlugin {
        val pluginInstance = loadJar(jarFile, fallbackPluginClassName, forceBypassSecurity)
        initializePlugin(pluginInstance)
        return pluginInstance
    }

    fun initializePlugin(pluginInstance: BasePlugin) {
        if (pluginInstance is Plugin) {
            pluginInstance.load(DesktopContextProvider.context)
        } else {
            pluginInstance.load()
        }
    }

    fun unloadPlugin(absolutePath: String) {
        val plugin = plugins[absolutePath]
        if (plugin == null) return

        try {
            plugin.beforeUnload()
        } catch (t: Throwable) {
            AppLogger.i("Failed to run beforeUnload for $absolutePath: ${t.message}")
        }

        // Close the ClassLoader to release file locks on Windows
        val classLoader = plugin.javaClass.classLoader
        if (classLoader != null) {
            classLoaders.remove(classLoader) // Fix Metaspace Leak!
            if (classLoader is URLClassLoader) {
                try {
                    classLoader.close()
                } catch (t: Throwable) {
                    AppLogger.i("Failed to close URLClassLoader for $absolutePath: ${t.message}")
                }
            }
        }

        // Remove providers and mappings registered by this plugin
        try {
            // APIHolder and extractorApis live in the library module
            com.lagradost.cloudstream3.APIHolder.apis.filter { it.sourcePlugin == plugin.filename }.forEach {
                com.lagradost.cloudstream3.APIHolder.removePluginMapping(it)
            }
            synchronized(com.lagradost.cloudstream3.APIHolder.allProviders) {
                com.lagradost.cloudstream3.APIHolder.allProviders.removeIf { it.sourcePlugin == plugin.filename }
            }
        } catch (t: Throwable) {
            AppLogger.i("Failed to remove plugin mappings for $absolutePath: ${t.message}")
        }

        try {
            // extractorApis
            synchronized(com.lagradost.cloudstream3.utils.extractorApis) {
                com.lagradost.cloudstream3.utils.extractorApis.removeIf { it.sourcePlugin == plugin.filename }
            }
        } catch (t: Throwable) {
            // ignore
        }

        try {
            // VideoClickActionHolder
            com.lagradost.cloudstream3.actions.VideoClickActionHolder.allVideoClickActions.removeIf { it.sourcePlugin == plugin.filename }
        } catch (t: Throwable) {
            // ignore
        }

        // Remove from tracked plugins
        plugins.remove(absolutePath)
    }

    fun isPluginLoaded(absolutePath: String): Boolean = plugins.containsKey(absolutePath)

    fun getPlugin(absolutePath: String): BasePlugin? = plugins[absolutePath]

    /**
     * Loads any extension jars on disk that are not already in memory (e.g. after sync/install).
     */
    fun rescanAndLoadNewPlugins(extensionsDir: File): Int {
        if (!extensionsDir.exists()) return 0

        var loaded = 0
        extensionsDir.walkTopDown()
            .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
            .filter { !it.name.endsWith("-jvm.jar") }
            .forEach { jar ->
                if (!isPluginLoaded(jar.absolutePath)) {
                    try {
                        loadAndInit(jar)
                        loaded++
                        AppLogger.i("Rescan: loaded ${jar.name}")
                    } catch (e: Throwable) {
                        AppLogger.i("Rescan: failed ${jar.name}: ${e.message}")
                    }
                }
            }
        return loaded
    }
}

abstract class VerifiedRepoMixIn {
    @com.fasterxml.jackson.annotation.JsonCreator
    constructor(
        @com.fasterxml.jackson.annotation.JsonProperty("url") url: String?,
        @com.fasterxml.jackson.annotation.JsonProperty("verified") verified: Boolean?
    )
}

