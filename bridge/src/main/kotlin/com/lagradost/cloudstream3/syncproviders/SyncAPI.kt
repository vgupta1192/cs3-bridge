package com.lagradost.cloudstream3.syncproviders

/**
 * Headless stub of the app-module SyncAPI base class. Some plugin jars bundle
 * their own copy of this class; the parent-first SafePluginClassLoader picks the
 * bridge's version so casts between stubs and plugin classes stay consistent.
 */
abstract class SyncAPI(
    open val name: String,
    open val requireLogin: Boolean = true,
    open val isReadOnly: Boolean = false,
) {
    open fun logOut() {}
    open fun authStatus(): Any? = null
    open fun isAuthenticated(): Boolean = false
}
