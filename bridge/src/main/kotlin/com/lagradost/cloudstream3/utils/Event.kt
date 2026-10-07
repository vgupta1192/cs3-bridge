package com.lagradost.cloudstream3.utils

/**
 * Headless stand-in for the app-module Event. Plugins subscribe with
 * `MainActivity.afterPluginsLoadedEvent += { ... }` (plusAssign), the app
 * fires events by invoking them; headless they simply never fire.
 */
class Event<T> {
    private val listeners = mutableListOf<(T) -> Unit>()

    operator fun plusAssign(listener: (T) -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    operator fun minusAssign(listener: (T) -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    operator fun invoke(value: T) {
        val current = synchronized(listeners) { listeners.toList() }
        for (l in current) runCatching { l(value) }
    }

    fun clear() = synchronized(listeners) { listeners.clear() }
}
