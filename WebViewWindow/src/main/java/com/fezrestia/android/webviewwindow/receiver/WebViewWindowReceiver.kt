@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.service.WebViewWindowService

class WebViewWindowReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceive()")

        val action = intent.action

        if (action == null) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION is NULL.")
            // NOP.
        } else {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION = $action")
            when (action) {
                Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY -> {
                    val service = Intent(Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY)
                    service.setClass(
                        context.applicationContext,
                        WebViewWindowService::class.java)
                    context.startForegroundService(service)
                }

                else -> throw IllegalArgumentException("Unexpected ACTION = $action")
            }
        }
    }

    companion object {
        private const val TAG = "WebViewWindowReceiver"
    }
}
