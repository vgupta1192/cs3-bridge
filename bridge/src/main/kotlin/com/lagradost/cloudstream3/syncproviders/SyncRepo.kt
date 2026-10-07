package com.lagradost.cloudstream3.syncproviders

/**
 * Headless stand-in for the app-module SyncRepo — the per-user wrapper around a
 * SyncAPI that sync plugins subclass or query (CineSimkl, CineStream, CineCat,
 * TorraStream, StreamPlay, ClipBox, WioCinema, StreamCenter).
 *
 * `library_IoAF18A` is the dex-era spelling of the suspend `library` call the
 * converted plugins reference (dex2jar keeps the underscore form); both
 * spellings are provided so either build resolves. Sync features are inert —
 * everything returns "not logged in".
 */
open class SyncRepo(val api: SyncAPI) {
    open fun getApi(): SyncAPI = api

    open fun authUser(): AuthUser? = null

    open fun authData(): AuthData? = null

    open fun getSyncIdName(): SyncIdName = SyncIdName.LocalList

    open suspend fun library(): Any? = null

    fun library_IoAF18A(continuation: kotlin.coroutines.Continuation<*>): Any? = null
}
