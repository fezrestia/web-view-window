@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.preference.PreferenceManager

import com.fezrestia.android.webviewwindow.Constants.FirebaseEvent.LowMemState
import com.fezrestia.android.webviewwindow.Constants.FirebaseEvent.RenderProcessState
import com.fezrestia.android.util.Log

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase

class App : Application() {
    override fun onCreate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : E")
        super.onCreate()

        // Create shared setting accessor.
        sp = PreferenceManager.getDefaultSharedPreferences(this)
        // Check version.
        val curVersion = sp.getInt(KEY_SP_VER, 0)
        if (curVersion != VAL_SP_VER) {
            sp.edit().clear().apply()
            sp.edit().putInt(KEY_SP_VER, VAL_SP_VER).apply()
        }

        // UI thread handler.
        ui = Handler(Looper.getMainLooper())

        // Firebase.
        FirebaseAnalyticsInterface.initialize()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : X")
    }

    override fun onTerminate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTerminate() : E")
        super.onTerminate()

        // NOP.

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTerminate() : X")
    }

    override fun onTrimMemory(level: Int) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTrimMemory() : E")
        super.onTrimMemory(level)

        FirebaseAnalyticsInterface.logOnTrimMemory(level)

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTrimMemory() : X")
    }

    companion object {
        private const val TAG = "App"

        lateinit var sp: SharedPreferences
            private set

        // SharedPreferences version key.
        private const val KEY_SP_VER = "key-sp-ver"
        private const val VAL_SP_VER = 2

        lateinit var ui: Handler
            private set

        // Global state flag.
        var isEnabled = false
    }
}

object FirebaseAnalyticsInterface {
    private const val TAG = "FirebaseAnalyticsInterface"

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    fun initialize() {
        firebaseAnalytics = Firebase.analytics
    }

    /**
     * Log onTrimMemory() event.
     *
     * @param level Argument of onTrimMemory().
     */
    fun logOnTrimMemory(level: Int) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnTrimMemory() : E")

        val firebaseParam = when (level) {
            // Application in foreground.
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnTrimMemory() : Level=TRIM_MEMORY_RUNNING_MODERATE")
                LowMemState.Params.Value.RUNNING_MODERATE
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnTrimMemory() : Level=TRIM_MEMORY_RUNNING_LOW")
                LowMemState.Params.Value.RUNNING_LOW
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnTrimMemory() : Level=TRIM_MEMORY_RUNNING_CRITICAL")
                LowMemState.Params.Value.RUNNING_CRITICAL
            }

            // UI visibility change.
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnTrimMemory() : Level=TRIM_MEMORY_UI_HIDDEN")
                LowMemState.Params.Value.UI_HIDDEN
            }

            // Application in background.
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnTrimMemory() : Level=TRIM_MEMORY_BACKGROUND")
                LowMemState.Params.Value.BACKGROUND
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnTrimMemory() : Level=TRIM_MEMORY_MODERATE")
                LowMemState.Params.Value.MODERATE
            }
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnTrimMemory() : Level=TRIM_MEMORY_COMPLETE")
                LowMemState.Params.Value.COMPLETE
            }

            else -> {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnTrimMemory() : Level=else")
                LowMemState.Params.Value.ELSE
            }
        }

        firebaseAnalytics.logEvent(LowMemState.EVENT) {
            param(LowMemState.Params.Key.ON_TRIM_MEMORY, firebaseParam)
            param(FirebaseAnalytics.Param.VALUE, 1)
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnTrimMemory() : X")
    }

    fun logOnRenderProcessGone(detail: RenderProcessGoneDetail) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnRenderProcessGone() : E")

        val firebaseParam = if (detail.didCrash()) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnRenderProcessGone() : Crashed")

            RenderProcessState.Params.Value.CRASH
        } else {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnRenderProcessGone() : LMK")

            when (detail.rendererPriorityAtExit()) {
                WebView.RENDERER_PRIORITY_WAIVED -> {
                    RenderProcessState.Params.Value.LMK_ON_PRIORITY_WAIVED
                }

                WebView.RENDERER_PRIORITY_BOUND -> {
                    RenderProcessState.Params.Value.LMK_ON_PRIORITY_BOUND
                }

                WebView.RENDERER_PRIORITY_IMPORTANT -> {
                    RenderProcessState.Params.Value.LMK_ON_PRIORITY_IMPORTANT
                }

                else -> {
                    RenderProcessState.Params.Value.ELSE
                }
            }
        }

        firebaseAnalytics.logEvent(RenderProcessState.EVENT) {
            param(RenderProcessState.Params.Key.ON_RENDER_PROCESS_GONE, firebaseParam)
            param(FirebaseAnalytics.Param.VALUE, 1)
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "logOnRenderProcessGone() : X")
    }
}
