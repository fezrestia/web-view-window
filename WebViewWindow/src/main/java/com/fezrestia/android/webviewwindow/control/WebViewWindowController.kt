@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow.control

import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.view.WebViewWindowRootView

/**
 * Controller class.
 *
 * @constructor
 *
 */
class WebViewWindowController {

    private lateinit var view: WebViewWindowRootView

    /**
     * Release ALL references.
     */
    fun release() {
        // NOP.
    }

    /**
     * Start.
     *
     * @view
     */
    fun start(view: WebViewWindowRootView) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : E")

        loadPreferences()

        this.view = view

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
