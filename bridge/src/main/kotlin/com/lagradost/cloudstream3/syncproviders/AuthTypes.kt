package com.lagradost.cloudstream3.syncproviders

/**
 * Headless stand-ins for the app-module auth types referenced by sync-aware
 * plugins (StreamCenter, CineSimkl, TorraStream, StreamPlay, ...).
 */
class AuthUser(
    val id: String? = null,
    val name: String? = null,
    val image: String? = null,
)

class AuthData(
    val id: String? = null,
    val name: String? = null,
    val image: String? = null,
)

/** No auth providers exist headless; AccountManager.getAllApis() returns an empty array. */
open class AuthRepo {
    open val idInfo: SyncIdName = SyncIdName.LocalList
    open fun authUser(): AuthUser? = null
}
