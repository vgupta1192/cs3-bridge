package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.Event

/**
 * Headless stand-in for the app-module MainActivity class. Plugins touch
 * MainActivity.Companion for the app lifecycle events (Ultima backup utils,
 * doGior's IPTV); headless the events simply never fire. The getApp() used by
 * providers lives on the MainActivityKt facade (library MainActivity.kt).
 */
class MainActivity {
    companion object {
        @JvmStatic
        val afterPluginsLoadedEvent = Event<Any?>()

        @JvmStatic
        val bookmarksUpdatedEvent = Event<Any?>()

        @JvmStatic
        val reloadLibraryEvent = Event<Any?>()
    }
}
