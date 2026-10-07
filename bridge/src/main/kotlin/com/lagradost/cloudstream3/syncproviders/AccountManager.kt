package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi

/**
 * Headless stub of the app-module AccountManager. Only the members referenced
 * by plugins at class-load time are provided; sync features are inert.
 */
class AccountManager {
    companion object {
        private val simklApi = SimklApi()
        private val aniListApi = AniListApi()

        @JvmStatic
        fun getSimklApi(): SimklApi = simklApi

        @JvmStatic
        fun getAniListApi(): AniListApi = aniListApi

        /** No auth providers exist headless. */
        @JvmStatic
        fun getAllApis(): Array<AuthRepo> = emptyArray()

        @JvmStatic
        fun getSyncApis(): Array<SyncRepo> = emptyArray()
    }
}
