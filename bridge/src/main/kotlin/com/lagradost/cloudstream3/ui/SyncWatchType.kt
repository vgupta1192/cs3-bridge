package com.lagradost.cloudstream3.ui

/**
 * Headless stand-in for the app-module SyncWatchType (StreamCenter maps its
 * tracked lists onto it). Values match the upstream enum exactly.
 */
enum class SyncWatchType {
    WATCHING,
    COMPLETED,
    ONHOLD,
    DROPPED,
    PLANTOWATCH,
    REWATCHING,
    ;

    fun getStringRes(): Int = 0
}
