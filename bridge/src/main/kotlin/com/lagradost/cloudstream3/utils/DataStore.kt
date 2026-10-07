package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Headless stand-in for the app-module DataStore. Backed by the android-stubs
 * SharedPreferences (DesktopDataStore, file-persisted).
 * Only the member-extension forms are declared (matches how the app compiles them).
 */
object DataStore {
    private val mapper = jacksonObjectMapper()

    fun getFolderName(folder: String, path: String): String = "${folder}_$path"

    fun Context.getSharedPrefs(): SharedPreferences =
        getSharedPreferences("cloudstore", 0)

    fun Context.getDefaultSharedPrefs(): SharedPreferences =
        getSharedPreferences("cloudstore_settings", 0)

    fun Context.getKeys(folder: String): List<String> =
        getSharedPreferences("cloudstore", 0).all.keys
            .filter { it.startsWith(getFolderName(folder, "")) }
            .map { it.removePrefix(getFolderName(folder, "")) }

    fun Context.containsKey(folder: String, path: String): Boolean =
        getSharedPreferences("cloudstore", 0).contains(getFolderName(folder, path))

    fun Context.containsKey(path: String): Boolean =
        getSharedPreferences("cloudstore", 0).contains(getFolderName("", path))

    fun Context.removeKey(folder: String, path: String) {
        getSharedPreferences("cloudstore", 0).edit().remove(getFolderName(folder, path)).apply()
    }

    fun Context.removeKey(path: String) {
        getSharedPreferences("cloudstore", 0).edit().remove(getFolderName("", path)).apply()
    }

    fun Context.removeKeys(folder: String): Int {
        val prefs = getSharedPreferences("cloudstore", 0)
        val keys = prefs.all.keys.filter { it.startsWith(getFolderName(folder, "")) }
        keys.forEach { prefs.edit().remove(it).apply() }
        return keys.size
    }

    fun <T> Context.setKey(path: String, value: T) = setKey("", path, value)

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        val prefs = getSharedPreferences("cloudstore", 0)
        val v = if (value is String) value else mapper.writeValueAsString(value)
        prefs.edit().putString(getFolderName(folder, path), v).apply()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? {
        val raw = getSharedPreferences("cloudstore", 0)
            .getString(getFolderName("", path), null) ?: return null
        return runCatching {
            if (valueType == String::class.java) raw as T
            else mapper.readValue(raw, valueType) as T
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> Context.getKey(path: String, defVal: T?): T? {
        val raw = getSharedPreferences("cloudstore", 0)
            .getString(getFolderName("", path), null) ?: return defVal
        return runCatching {
            if (defVal is String) raw as T else mapper.readValue(raw, defVal!!::class.java as Class<T>) as T
        }.getOrNull() ?: defVal
    }

    fun <T : Any> String.toKotlinObject(valueType: Class<T>): T? =
        runCatching { mapper.readValue(this, valueType) }.getOrNull()
}
