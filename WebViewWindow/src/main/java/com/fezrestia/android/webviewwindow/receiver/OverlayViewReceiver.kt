@file:Suppress("ConstantConditionIf", "MayBeConstant")

package com.fezrestia.android.webviewwindow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.control.OverlayViewController
import com.fezrestia.android.util.Log

class OverlayViewReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceive()")

        val action = intent.action

        if (action == null) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION is NULL.")
            // NOP.
        } else {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION = $action")
            when (action) {
                Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY ->
                    OverlayViewController.singleton.toggleVisibility()

                else ->
                    // Unexpected Action.
                    throw IllegalArgumentException("Unexpected Action : $action")
            }
        }
    }

    companion object {
        // Log tag.
        private val TAG = "OverlayViewReceiver"
    }
}
