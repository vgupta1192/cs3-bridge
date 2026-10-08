package com.lagradost.api

actual object Log {
    // plugin debug output (whole JSON/HTML bodies) was MBs per minute of log
    // churn; CSBRIDGE_PLUGIN_DEBUG=1 brings it back
    private val debug = System.getenv("CSBRIDGE_PLUGIN_DEBUG") == "1"

    actual fun d(tag: String, message: String) {
        if (debug) println("DEBUG $tag: $message")
    }

    actual fun i(tag: String, message: String) {
        println("INFO $tag: $message")
    }

    actual fun w(tag: String, message: String) {
        println("WARNING $tag: $message")
    }

    actual fun e(tag: String, message: String) {
        println("ERROR $tag: $message")
    }
}