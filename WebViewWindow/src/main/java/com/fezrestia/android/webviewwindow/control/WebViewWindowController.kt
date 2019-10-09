@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow.control

import com.fezrestia.android.util.Log

class WebViewWindowController {

    /**
     * Start.
     */
    fun start() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : E")

        loadPreferences()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : X")
    }

    private fun loadPreferences() {
        // NOP.
    }

    /**
     * Stop.
     */
    fun stop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : E")

        // NOP.

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : X")
    }

    companion object {
        private const val TAG = "WebViewWindowController"
    }
}
