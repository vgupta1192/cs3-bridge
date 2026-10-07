@file:OptIn(com.lagradost.cloudstream3.Prerelease::class, com.lagradost.cloudstream3.UnsafeSSL::class)

package com.kissmissi.csbridge

import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.common.logging.AppLogger
import com.lagradost.nicehttp.ignoreAllSSLErrors
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object Boot {
    fun init() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("Unhandled exception in ${thread.name}", throwable)
        }

        // Android AES-GCM / crypto compat, same as the desktop port
        runCatching { java.security.Security.insertProviderAt(org.bouncycastle.jce.provider.BouncyCastleProvider(), 1) }

        // Global NiceHttp clients (mirrors cloudstream-desktop NetworkConfig, without DoH)
        val baseBuilder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(CloudflareKiller())

        app.baseClient = baseBuilder.build()
        app.defaultHeaders = mapOf("user-agent" to USER_AGENT)

        val insecureBuilder = app.baseClient.newBuilder()
        runCatching { insecureBuilder.ignoreAllSSLErrors() }
        insecureApp.baseClient = insecureBuilder.build()
        insecureApp.defaultHeaders = mapOf("user-agent" to USER_AGENT)

        java.util.logging.Logger.getLogger(OkHttpClient::class.java.name).level = java.util.logging.Level.SEVERE
        java.util.logging.Logger.getLogger(okhttp3.internal.platform.Platform::class.java.name).level = java.util.logging.Level.SEVERE

        Cfg.extensionsDir.mkdirs()
        File(Cfg.dataDir, "prefs").mkdirs()
        // java.util.prefs is used by ExtensionLoader's trusted-plugin store
        System.setProperty("java.util.prefs.userRoot", File(Cfg.dataDir, "prefs").absolutePath)
    }
}
