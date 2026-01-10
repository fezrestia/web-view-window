package com.fezrestia.android.util

import com.fezrestia.android.webviewwindow.BuildConfig

object Log {
    // All area total log trigger.
    val IS_DEBUG = BuildConfig.DEBUG

    /**
     * Debug log.
     *
     * @param tag Log tag.
     * @param event Log event.
     */
    fun logDebug(tag: String, event: String) {
        log("DEBUG", tag, event)
    }

    /**
     * Error log.
     *
     * @param tag Log tag.
     * @param event Log event.
     */
    fun logError(tag: String, event: String) {
        log("ERROR", tag, event)
    }

    private fun log(globalTag: String, localTag: String, event: String) {
        val builder = StringBuilder().append("[").append(globalTag).append("] ")
                .append("[TIME = ").append(System.currentTimeMillis()).append("] ")
                .append("[").append(localTag).append("]")
                .append("[").append(Thread.currentThread().name).append("] ")
                .append(": ").append(event)
        android.util.Log.e("TraceLog", builder.toString())
    }
}
