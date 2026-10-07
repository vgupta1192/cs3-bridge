package com.lagradost.cloudstream3.utils

/**
 * Headless stand-ins for the app-module UiText (doGior plugins wrap display
 * strings with txt()).
 */
sealed class UiText {
    data class DynamicString(val value: String) : UiText()
    data class StringResource(val resId: Int, var args: List<Any> = emptyList()) : UiText()
}
