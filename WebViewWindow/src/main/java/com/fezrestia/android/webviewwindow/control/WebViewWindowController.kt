@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow.control

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.activity.ChromeCustomTabBaseActivity
import com.fezrestia.android.webviewwindow.view.WebViewWindowRootView

/**
 * Controller class.
 *
 * @constructor
 * @param context
 */
class WebViewWindowController(private val context: Context) {

    var view: WebViewWindowRootView? = null

    /**
     * Release ALL references.
     */
    fun release() {
        // NOP.
    }

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

    /**
     * Start Chrome Custom Tab.
     *
     * @url Target URL.
     */
    fun startChromeCustomTab(url: String) {
        val baseAct = Intent(
                Intent.ACTION_MAIN,
                Uri.parse(url),
                context,
                ChromeCustomTabBaseActivity::class.java)
        baseAct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        baseAct.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        baseAct.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        context.startActivity(baseAct)
    }
}
