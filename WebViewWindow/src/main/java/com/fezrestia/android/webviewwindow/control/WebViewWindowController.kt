@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow.control

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.util.Base64
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
        // Fail safe.
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    /**
     * Activate overlay window interaction.
     * After this API called, WakeLock is acquired until stop() is called.
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
     * De-activate overlay window interaction.
     * WakeLock will be released, so WebFrame contents may be paused/stopped.
     */
    fun stop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : E")

        // Remove update task.
        updateWakeLockTask?.let { task ->
            App.ui.removeCallbacks(task)
            updateWakeLockTask = null
        }
        if (wakeLock.isHeld) {
            wakeLock.release()
        }

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
     * Save WebView states to SharedPreferences.
     *
     * @param states
     */
    fun saveWebViewStates(states: List<Bundle>) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "saveWebViewStates() : E")

        val encodedList = mutableListOf<String>()

        states.forEach { state ->
            state[Constants.WEB_VIEW_STATE_BUNDLE_KEY]?.let { it ->
                val bytes = it as ByteArray
                val encoded: String = Base64.encodeToString(bytes, Base64.DEFAULT)

                if (Log.IS_DEBUG) Log.logDebug(TAG, "BASE64 = $encoded")

                encodedList.add(encoded)
            }
        }

        if (encodedList.isNotEmpty()) {
            val jsonArray = JSONArray(encodedList)
            val serialized = jsonArray.toString()

            App.sp.edit().putString(
                    Constants.SP_KEY_LAST_WEB_VIEW_STATES_JSON,
                    serialized)
                    .apply()
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "saveWebViewStates() : X")
    }

    /**
     * Load WebView states from SharedPreferences.
     *
     * @return WebView states.
     */
    fun loadWebViewStates(): List<Bundle> {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "loadWebViewStates() : E")

        val serialized = App.sp.getString(Constants.SP_KEY_LAST_WEB_VIEW_STATES_JSON, "[]") // Default empty.
        val deserialized = JSONArray(serialized)

        val results = mutableListOf<Bundle>() // Empty, used for default.

        for (i in 0 until deserialized.length()) {
            val encoded: String = deserialized.get(i) as String

            if (Log.IS_DEBUG) Log.logDebug(TAG, "BASE64 = $encoded")

            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val state = Bundle()
            state.putByteArray(Constants.WEB_VIEW_STATE_BUNDLE_KEY, bytes)
            results.add(state)
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "loadWebViewStates() : X")
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
