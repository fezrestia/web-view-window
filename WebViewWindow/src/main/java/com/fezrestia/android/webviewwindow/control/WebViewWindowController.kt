@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow.control

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
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

    private val wakeLock: PowerManager.WakeLock
    private var updateWakeLockTask: UpdateWakeLockTask? = null

    init {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                Constants.WAKE_LOCK_NAME)
    }

    /**
     * Release ALL references.
     */
    fun release() {
        wakeLock.release() // Fail safe.
    }

    /**
     * Start.
     */
    fun start() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : E")

        // Acquire WakeLock immediately.
        UpdateWakeLockTask().let { task ->
            App.ui.post(task)
            updateWakeLockTask = task
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : X")
    }

    /**
     * Stop.
     */
    fun stop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : E")

        // Remove update task.
        updateWakeLockTask?.let { task ->
            App.ui.removeCallbacks(task)
            updateWakeLockTask = null
        }
        wakeLock.release()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : X")
    }

    private inner class UpdateWakeLockTask : Runnable {
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "UpdateWakeLockTask.run()")

            wakeLock.acquire(WAKE_LOCK_INTERVAL_MILLIS + 1000)
            App.ui.postDelayed(this, WAKE_LOCK_INTERVAL_MILLIS)
        }
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

    companion object {
        private const val TAG = "WebViewWindowController"

        private const val WAKE_LOCK_INTERVAL_MILLIS: Long = 30 * 60 * 1000

    }
}
