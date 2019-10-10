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

class WebViewWindowRootView(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int) : FrameLayout(context, attrs, defStyle) {

    // Web.
    private lateinit var webView: ExtendedWebView

    // Display size.
    private lateinit var displaySize: FrameSize

    // Display orientation.
    private enum class Orientation {
        PORTRAIT,
        LANDSCAPE,
    }
    private lateinit var displayOrientation: Orientation

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

        initializeInstances()
        createWindowParameters()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : X")
    }

    private fun initializeInstances() {
        // Web view.
        webView = ExtendedWebView(context)
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

        webView.release()
    }

    /**
     * Add this view to WindowManager layer.
     */
    fun addToOverlayWindow() {
        // Update display configurations and layout parameters.
        updateTotalUserInterface()

        // Add to WindowManager.
        windowManager.addView(this, windowLayoutParams)
    }

    /**
     * Remove this view from WindowManager layer.
     */
    fun removeFromOverlayWindow() {
        windowManager.removeView(this)
    }

    private fun updateDisplayConfig() {
        // Get display size.
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)

        displaySize = FrameSize(size.x, size.y)
        if (Log.IS_DEBUG) Log.logDebug(TAG, "updateDisplayConfig() : $displaySize")

        // Get display displayOrientation.
        displayOrientation = if (displaySize.height < displaySize.width) {
            Orientation.LANDSCAPE
        } else {
            Orientation.PORTRAIT
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun updateWindowParams() {
        windowLayoutParams.gravity = Gravity.LEFT or Gravity.TOP

        // Window size.
        when (displayOrientation) {
            Orientation.PORTRAIT -> {
                windowLayoutParams.width = displaySize.shortLine
                windowLayoutParams.height = (displaySize.longLine * SCREEN_LONG_LINE_CLEARANCE).toInt()

                windowLayoutParams.y = (displaySize.longLine - windowLayoutParams.height) / 2
            }

            Orientation.LANDSCAPE -> {
                var statusBarHeight = 0
                val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resourceId > 0) {
                    statusBarHeight = resources.getDimensionPixelSize(resourceId)
                }

                windowLayoutParams.width = (displaySize.longLine * SCREEN_LONG_LINE_CLEARANCE).toInt()
                windowLayoutParams.height = displaySize.shortLine - statusBarHeight

                windowLayoutParams.y = (displaySize.shortLine - windowLayoutParams.height) / 2
            }
        }

        // Window show/hide constants.
        windowOpenPosX = 0
        windowClosePosX = -1 * (windowLayoutParams.width - SLIDER_GRIP_WIDTH_PIX)

        // Initial values
        windowLayoutParams.x = windowOpenPosX

        if (Log.IS_DEBUG) {
            val w = windowLayoutParams.width
            val h = windowLayoutParams.height
            Log.logDebug(TAG, "updateWindowParams() : WinSize WxH = $w x $h")
        }
    }

    private fun updateLayoutParams() {
        val containerParams = web_view_container.layoutParams
        containerParams.width = windowLayoutParams.width - SLIDER_GRIP_WIDTH_PIX
        containerParams.height = windowLayoutParams.height
        web_view_container.layoutParams = containerParams
    }

    private fun updateTotalUserInterface() {
        updateDisplayConfig()
        updateWindowParams()
        updateLayoutParams()

        // After screen displayOrientation changed or something, always close overlay view.
        if (isAttachedToWindow) {
            windowLayoutParams.x = windowClosePosX
            windowManager.updateViewLayout(this, windowLayoutParams)
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
                        windowManager.updateViewLayout(this@WebViewWindowRootView, windowLayoutParams)
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
                    if (displaySize.shortLine / 2 < event.rawX) {
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
                            this@WebViewWindowRootView,
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
        private const val TAG = "WebViewWindowRootView"

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

        // Screen long line clearance.
        private const val SCREEN_LONG_LINE_CLEARANCE = 0.8f

        // Grip width.
        private const val SLIDER_GRIP_WIDTH_PIX = 64

        // Hidden window position constants.
        private const val WINDOW_HIDDEN_POS_X = -5000
    }
}
