package com.lagradost.cloudstream3

import android.content.Context
import android.content.DesktopContextProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Headless stand-in for the CloudStream app class. Plugins compiled against newer
 * upstream reference `CloudStreamApp` for settings storage and context access.
 * Values persist through the android-stubs SharedPreferences -> DesktopDataStore.
 */
class CloudStreamApp private constructor() {
    companion object {
        private val mapper = jacksonObjectMapper()

        @JvmStatic
        var context: Context = DesktopContextProvider.context

        fun setKey(path: String, value: Any?) {
            val prefs = context.getSharedPreferences("cloudstream_settings", 0)
            val v = if (value is String) value else mapper.writeValueAsString(value)
            prefs.edit().putString("single__$path", v).apply()
        }

        fun setKey(folder: String, path: String, value: Any?) {
            val prefs = context.getSharedPreferences("cloudstream_settings", 0)
            val v = if (value is String) value else mapper.writeValueAsString(value)
            prefs.edit().putString("${folder}__$path", v).apply()
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? {
            val prefs = context.getSharedPreferences("cloudstream_settings", 0)
            val raw = prefs.getString("single__$path", null) ?: return null
            return runCatching {
                if (valueType == String::class.java) raw as T
                else mapper.readValue(raw, valueType) as T
            }.getOrNull()
        }

        fun <T : Any> setKeyClass(path: String, value: T) = setKey(path, value)

        fun <T : Any> getKey(path: String): T? = getKeyClass(path, Any::class.java as Class<T>)

        fun removeKeys(folder: String): Int? {
            val prefs = context.getSharedPreferences("cloudstream_settings", 0)
            var n = 0
            prefs.all.keys.filter { it.startsWith("${folder}__") }.forEach {
                prefs.edit().remove(it).apply(); n++
            }
            return n
        }

        fun getKeys(folder: String): List<String>? =
            context.getSharedPreferences("cloudstream_settings", 0).all.keys
                .filter { it.startsWith("${folder}__") }.map { it.removePrefix("${folder}__") }

        fun removeKey(folder: String, path: String) {
            context.getSharedPreferences("cloudstream_settings", 0).edit().remove("${folder}__$path").apply()
        }

        fun removeKey(path: String) {
            context.getSharedPreferences("cloudstream_settings", 0).edit().remove("single__$path").apply()
        }

        fun openBrowser(url: String, fallbackWebView: Boolean = false, fragment: Any? = null) {
            com.lagradost.common.logging.AppLogger.i("CloudStreamApp.openBrowser ignored: $url")
        }

        fun openBrowser(url: String, activity: Any?) = openBrowser(url)
    }
}
