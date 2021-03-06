@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager

import com.fezrestia.android.util.Log

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

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : X")
    }

    override fun onTerminate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTerminate() : E")
        super.onTerminate()

        // NOP.

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTerminate() : X")
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
