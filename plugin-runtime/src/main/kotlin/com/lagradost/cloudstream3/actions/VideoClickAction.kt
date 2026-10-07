package com.lagradost.cloudstream3.actions

open class VideoClickAction(
    val name: String,
    val iconId: Int,
    val requiresAuthentication: Boolean = false,
    val callback: () -> Unit,
) {
    var sourcePlugin: String? = null

    /** No-arg constructor for plugins that subclass this with their own defaults. */
    constructor() : this("", 0, false, {})
}
