package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.SimklApi

/**
 * Headless stub of the app-module AccountManager. Only the members referenced
 * by plugins at class-load time are provided; sync features are inert.
 */
class AccountManager {
    companion object {
        @JvmStatic
        fun getSimklApi(): SimklApi = SimklApi()
    }
}
