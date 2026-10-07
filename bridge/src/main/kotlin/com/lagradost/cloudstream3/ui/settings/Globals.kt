package com.lagradost.cloudstream3.ui.settings

/**
 * Minimal stand-in for the app-module Globals (layout constants).
 * Constant values are inlined at plugin compile time, so only isLayout matters.
 */
object Globals {
    const val PHONE = 0
    const val TV = 1
    const val EMULATOR = 2
    const val AUTO = 3

    fun isLayout(layout: Int): Boolean = layout == PHONE

    fun getLayout(): Int = PHONE
}
