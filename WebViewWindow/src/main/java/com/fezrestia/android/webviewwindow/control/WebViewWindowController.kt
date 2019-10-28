@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow.control

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.App
import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.activity.ChromeCustomTabBaseActivity
import org.json.JSONArray

/**
 * Controller class.
 *
 * @constructor
 * @param context
 */
class WebViewWindowController(private val context: Context) {
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

        // NOP.

        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : X")
    }

    /**
     * Stop.
     */
    fun stop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : E")

        // NOP.

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : X")
    }

    /**
     * Default load URL.
     *
     * @return URL.
     */
    fun getDefaultUrl(): String {
        var baseUrl = App.sp.getString(
                Constants.SP_KEY_BASE_LOAD_URL,
                Constants.DEFAULT_BASE_LOAD_URL) as String
        if (baseUrl.isEmpty()) {
            baseUrl = Constants.DEFAULT_BASE_LOAD_URL
        }

        return baseUrl
    }

    /**
     * Save URLs to SharedPreferences.
     *
     * @param urls
     */
    fun saveUrls(urls: List<String>) {
        val jsonArray = JSONArray(urls)
        val serialized = jsonArray.toString()

        App.sp.edit().putString(
                Constants.SP_KEY_LAST_URLS_JSON,
                serialized)
                .apply()
    }

    /**
     * Load URLs from SharedPreferences.
     *
     * @return URLs
     */
    fun loadUrls(): List<String> {
        val serialized = App.sp.getString(Constants.SP_KEY_LAST_URLS_JSON, "[]") // Default empty.
        val deserialized = JSONArray(serialized)

        val results = mutableListOf<String>() // Empty, used for default.

        for (i in 0 until deserialized.length()) {
            val url: String = deserialized.get(i) as String
            results.add(url)
        }

        return results
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
