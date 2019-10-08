@file:Suppress("ConstantConditionIf", "PrivatePropertyName")

package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Handler
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.App
import com.fezrestia.android.util.FrameSize
import com.fezrestia.android.util.Log
import kotlinx.android.synthetic.main.overlay_root_view.view.*

class OverlayRootView(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int) : FrameLayout(context, attrs, defStyle) {

    // Web.
    private lateinit var webView: UserWebView

    // Screen coordinates.
    private lateinit var screenSize: FrameSize

    // Overlay window orientation.
    private var orientation = Configuration.ORIENTATION_UNDEFINED

    // Window.
    private lateinit var windowManager: WindowManager
    private lateinit var windowLayoutParams: WindowManager.LayoutParams

    // Window position.
    private var windowOpenPosX = 0
    private var windowClosePosX = 0

    // Window position correction animation.
    private var windowPositionCorrectionTask: WindowPositionCorrectionTask? = null

    // CONSTRUCTOR.
    constructor(context: Context) : this(context, null) {
        // NOP.
    }

    // CONSTRUCTOR.
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {
        // NOP.
    }

    /**
     * Initialize all of configurations.
     */
    fun initialize() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : E")

        // Cache instance references.
        initializeInstances()

        // Load setting.
        loadPreferences()

        // Window related.
        createWindowParameters()

        // Update UI.
        updateTotalUserInterface()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : X")
    }

    @SuppressLint("RtlHardcoded")
    private fun initializeInstances() {
        // Web view.
        webView = UserWebView(context)
        webView.initialize()
        val url = App.sp.getString(
                Constants.SP_KEY_BASE_LOAD_URL,
                Constants.DEFAULT_BASE_LOAD_URL) as String
        if (url.isEmpty()) {
            webView.loadUrl(Constants.DEFAULT_BASE_LOAD_URL)
        } else {
            webView.loadUrl(url)
        }

        val webViewParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        web_view_container.addView(webView, webViewParams)

        // Slider grip.
        slider_grip_container.setOnTouchListener(SliderGripTouchEventHandler())
    }

    private fun loadPreferences() {
        // NOP.
    }

    private fun createWindowParameters() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        windowLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                INTERACTIVE_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT)
    }

    /**
     * Release all resources.
     */
    fun release() {
        slider_grip_container.setOnTouchListener(null)
        setOnTouchListener(null)

        webView.release()
    }

    /**
     * Add this view to WindowManager layer.
     */
    fun addToOverlayWindow() {
        // Window parameters.
        updateWindowParams()

        // Add to WindowManager.
        windowManager.addView(this, windowLayoutParams)
    }

    /**
     * Remove this view from WindowManager layer.
     */
    fun removeFromOverlayWindow() {
        windowManager.removeView(this)
    }

    private fun updateWindowParams() {
        windowLayoutParams.width = screenSize.shortLineSize
        windowLayoutParams.height = screenSize.shortLineSize - SLIDER_GRIP_WIDTH_PIX
        windowLayoutParams.gravity = Gravity.CENTER

        // Window show/hide constants.
        windowOpenPosX = 0
        windowClosePosX = -1 * (screenSize.shortLineSize - SLIDER_GRIP_WIDTH_PIX)

        windowLayoutParams.x = windowOpenPosX
        windowLayoutParams.y = 0

        if (Log.IS_DEBUG)
            Log.logDebug(TAG,
                    "updateWindowParams() : WinSizeWxH="
                            + windowLayoutParams.width + "x" + windowLayoutParams.height)

        if (isAttachedToWindow) {
            windowManager.updateViewLayout(this, windowLayoutParams)
        }
    }

    private fun updateLayoutParams() {
        // Container size.
        web_view_container.layoutParams.width = screenSize.shortLineSize - SLIDER_GRIP_WIDTH_PIX
        web_view_container.layoutParams.height = screenSize.shortLineSize - SLIDER_GRIP_WIDTH_PIX

        // Contents size.
        val webViewLayoutParams = webView.layoutParams as LayoutParams
        webViewLayoutParams.width = screenSize.shortLineSize - SLIDER_GRIP_WIDTH_PIX
        webViewLayoutParams.height = screenSize.shortLineSize - SLIDER_GRIP_WIDTH_PIX
        webViewLayoutParams.gravity = Gravity.CENTER
        webView.layoutParams = webViewLayoutParams
    }

    private fun updateTotalUserInterface() {
        // Screen configuration.
        calculateScreenConfiguration()
        // Window layout.
        updateWindowParams()
        // UI layout.
        updateLayoutParams()

        // After screen orientation changed or something, always hide overlay view.
        if (isAttachedToWindow) {
            windowLayoutParams.x = WINDOW_HIDDEN_POS_X
            windowManager.updateViewLayout(this, windowLayoutParams)
        }
    }

    private fun calculateScreenConfiguration() {
        // Get display size.
        val display = windowManager.defaultDisplay
        val screenSize = Point()
        display.getSize(screenSize)

        this.screenSize = FrameSize(screenSize.x, screenSize.y)

        if (Log.IS_DEBUG)
            Log.logDebug(TAG,
                    "calculateScreenConfiguration() : " + this.screenSize.toString())

        // Get display orientation.
        orientation = if (this.screenSize.height < this.screenSize.width) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
    }

    fun toggleShowHide() {
        if (windowLayoutParams.x == WINDOW_HIDDEN_POS_X) {
            // Show.
            windowLayoutParams.x = windowClosePosX
            windowLayoutParams.flags = INTERACTIVE_WINDOW_FLAGS
        } else {
            // Hide.
            windowLayoutParams.x = WINDOW_HIDDEN_POS_X
            windowLayoutParams.flags = NOT_INTERACTIVE_WINDOW_FLAGS
        }
        windowManager.updateViewLayout(this, windowLayoutParams)
    }

    public override fun onConfigurationChanged(newConfig: Configuration) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onConfigurationChanged() : Config=$newConfig")
        super.onConfigurationChanged(newConfig)

        // Update UI.
        updateTotalUserInterface()
    }

    private inner class SliderGripTouchEventHandler : OnTouchListener {
        private var onDownWinPosX = 0
        private var onDownBasePosX = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    onDownBasePosX = event.rawX.toInt()
                    onDownWinPosX = windowLayoutParams.x
                }

                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.rawX.toInt() - onDownBasePosX
                    var nextWinPosX = onDownWinPosX + diffX

                    if (isAttachedToWindow) {
                        // Check limit.
                        if (nextWinPosX < windowClosePosX) {
                            nextWinPosX = windowClosePosX
                        }
                        if (windowOpenPosX < nextWinPosX) {
                            nextWinPosX = windowOpenPosX
                        }

                        // Update.
                        windowLayoutParams.x = nextWinPosX
                        windowManager.updateViewLayout(this@OverlayRootView, windowLayoutParams)
                    }
                }

                MotionEvent.ACTION_UP,
                    // fall-through.
                MotionEvent.ACTION_CANCEL -> {
                    // Reset.
                    onDownBasePosX = 0
                    onDownWinPosX = 0

                    // Check.
                    val task = windowPositionCorrectionTask
                    if (task != null) {
                        App.ui.removeCallbacks(task)
                    }

                    // Fixed position.
                    val targetPoint: Point
                    if (screenSize.shortLineSize / 2 < event.rawX) {
                        // To be opened.
                        targetPoint = Point(windowOpenPosX, windowLayoutParams.y)
                        windowLayoutParams.flags = INTERACTIVE_WINDOW_FLAGS
                    } else {
                        // To be closed.
                        targetPoint = Point(windowClosePosX, windowLayoutParams.y)
                        windowLayoutParams.flags = NOT_INTERACTIVE_WINDOW_FLAGS
                    }

                    // Start fix.
                    val newTask = WindowPositionCorrectionTask(
                            this@OverlayRootView,
                            targetPoint,
                            windowManager,
                            windowLayoutParams,
                            App.ui)
                    App.ui.post(newTask)
                    windowPositionCorrectionTask = newTask
                }

                else -> {
                    // NOP. Unexpected.
                }
            }

            return true
        }
    }

    private class WindowPositionCorrectionTask(
            // Target.
            private val targetView: View,
            private val targetWindowPosit: Point,
            // Environment.
            private val winMng: WindowManager,
            private val winParams: WindowManager.LayoutParams,
            private val ui: Handler) : Runnable {
        private val TAG = "WindowPositionCorrectionTask"

        // Last delta.
        private var lastDeltaX = 0
        private var lastDeltaY = 0

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            val dX = targetWindowPosit.x - winParams.x
            val dY = targetWindowPosit.y - winParams.y

            // Update layout.
            winParams.x += (dX * P_GAIN).toInt()
            winParams.y += (dY * P_GAIN).toInt()

            if (targetView.isAttachedToWindow) {
                winMng.updateViewLayout(
                        targetView,
                        winParams)
            } else {
                // Already detached from window.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already detached from window.")
                return
            }

            // Check next.
            if (lastDeltaX == dX && lastDeltaY == dY) {
                // Correction is already convergent.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already position fixed.")

                // Fix position.
                winParams.x = targetWindowPosit.x
                winParams.y = targetWindowPosit.y

                winMng.updateViewLayout(
                        targetView,
                        winParams)
                return
            }
            lastDeltaX = dX
            lastDeltaY = dY

            // Next.
            ui.postDelayed(this, WINDOW_ANIMATION_INTERVAL_MILLIS.toLong())

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }

        companion object {
            // Proportional gain.
            private const val P_GAIN = 0.2f

            // Animation refresh interval.
            private const val WINDOW_ANIMATION_INTERVAL_MILLIS = 16
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "KEYCODE_BACK : DOWN")
                    // NOP.
                }

                KeyEvent.ACTION_UP -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "KEYCODE_BACK : UP")

                    // Go back on WebView.
                    if (webView.canGoBack()) {
                        webView.goBack()
                    }
                }

                else -> {
                    // NOP.
                }
            }

            else -> {
                // NOP.
            }
        }

        return true
    }

    companion object {
        private const val TAG = "OverlayRootView"

        private const val INTERACTIVE_WINDOW_FLAGS = ( 0 // Dummy
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        private const val NOT_INTERACTIVE_WINDOW_FLAGS = ( 0 // Dummy
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // Grip width.
        private const val SLIDER_GRIP_WIDTH_PIX = 1080 - 960

        // Hidden window position constants.
        private const val WINDOW_HIDDEN_POS_X = -5000
    }
}
