@file:Suppress("ConstantConditionIf", "MayBeConstant")

package com.fezrestia.android.webviewwindow.control

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater

import com.fezrestia.android.webviewwindow.R
import com.fezrestia.android.webviewwindow.view.OverlayRootView
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.App

class OverlayViewController private constructor() {

    // Overlay view.
    private var overlayView: OverlayRootView? = null

    /**
     * Start overlay.
     *
     * @param context Master context.
     */
    @SuppressLint("InflateParams")
    fun start(context: Context) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : E")

        var view: OverlayRootView? = overlayView

        if (view != null) {
            // NOP. Already started.
            Log.logError(TAG, "Error. Already started.")
            return
        }

        // Load setting.
        loadPreferences()

        // Create overlay view.
        view = LayoutInflater.from(context).inflate(
                R.layout.overlay_root_view,
                null) as OverlayRootView
        view.initialize()
        view.addToOverlayWindow()

        overlayView = view

        App.isEnabled = true

        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : X")
    }

    private fun loadPreferences() {
        // NOP.
    }

    /**
     * Stop overlay.
     */
    fun stop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : E")

        val view: OverlayRootView? = overlayView

        if (view == null) {
            // NOP. Already stopped.
            Log.logError(TAG, "Error. Already stopped.")
            return
        }

        // Release references.
        view.release()
        view.removeFromOverlayWindow()

        overlayView = null

        App.isEnabled = false

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : X")
    }

    /**
     * Toggle overlay view visibility.
     */
    fun toggleVisibility() {
        overlayView?.toggleShowHide()
    }

    companion object {
        private val TAG = "OverlayViewController"

        val singleton = OverlayViewController()

    }
}
