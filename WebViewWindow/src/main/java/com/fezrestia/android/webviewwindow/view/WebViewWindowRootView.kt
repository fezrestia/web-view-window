@file:Suppress("ConstantConditionIf", "PrivatePropertyName")

package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.App
import com.fezrestia.android.util.LayoutRect
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.R
import kotlinx.android.synthetic.main.overlay_root_view.view.*

class WebViewWindowRootView(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int) : FrameLayout(context, attrs, defStyle) {

    // Grip size.
    private val SLIDER_GRIP_WIDTH_PIX = resources.getDimensionPixelSize(R.dimen.grip_width)
    private val SLIDER_GRIP_HEIGHT_PIX = resources.getDimensionPixelSize(R.dimen.grip_height)

    // Display size.
    private lateinit var displaySize: LayoutRect
    // Status bar.
    private var statusBarSize = 0

    // Display orientation.
    private enum class Orientation {
        PORTRAIT,
        LANDSCAPE,
    }
    private lateinit var displayOrientation: Orientation

    // Window.
    private lateinit var windowManager: WindowManager
    private lateinit var windowLayoutParams: WindowManager.LayoutParams

    // Window layout.
    private val openedWindowLayout = LayoutRect(0, 0, 0, 0)
    private val closedWindowLayout = LayoutRect(0, 0, 0, 0)

    // Animation per-frame task.
    private var windowStateConvergentTask: WindowStateConvergentTask? = null

    // Web frames.
    private val webFrames: MutableList<WebFrame> = mutableListOf()

    // Current top WebFrame.
    private lateinit var topWebFrame: WebFrame


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






        // Resizer grip.
        resizer_grip.setOnTouchListener(ResizerGripTouchEventHandler())
    }

    private fun createWindowParameters() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        windowLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                INTERACTIVE_WINDOW_FLAGS, // Initialize as opened state.
                PixelFormat.TRANSLUCENT)
    }

    /**
     * Release all resources.
     */
    fun release() {
        web_frame_container.removeAllViews()
        webFrames.forEach(WebFrame::release)
        webFrames.clear()
        resizer_grip.setOnTouchListener(null)
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

    /**
     * Add new WebFrame to tail of stack.
     */
    fun addNewWebFrame() {
        var baseUrl = App.sp.getString(
                Constants.SP_KEY_BASE_LOAD_URL,
                Constants.DEFAULT_BASE_LOAD_URL) as String
        if (baseUrl.isEmpty()) {
            baseUrl = Constants.DEFAULT_BASE_LOAD_URL
        }

        val webFrame = WebFrame.inflate(context)
        webFrame.initialize(
                WebFrameCallbackImpl(),
                baseUrl)

        webFrames.add(webFrame)
        webFrame.setFrameOrder(webFrames.indexOf(webFrame), webFrames.size)

        web_frame_container.addView(webFrame)

        // TODO: Consider multi tab Z-order.
        topWebFrame = webFrame

    }

    private fun updateDisplayConfig() {
        // Get display size.
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)

        displaySize = LayoutRect(0, 0, size.x, size.y)
        if (Log.IS_DEBUG) Log.logDebug(TAG, "updateDisplayConfig() : $displaySize")

        // Get display displayOrientation.
        displayOrientation = if (displaySize.height < displaySize.width) {
            Orientation.LANDSCAPE
        } else {
            Orientation.PORTRAIT
        }

        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarSize = resources.getDimensionPixelSize(resourceId)
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
                windowLayoutParams.width = (displaySize.longLine * SCREEN_LONG_LINE_CLEARANCE).toInt()
                windowLayoutParams.height = displaySize.shortLine - statusBarSize * 2

                windowLayoutParams.y = statusBarSize / 2
            }
        }

        // Window open/close X-Y / W-H.
        updateWindowOpenCloseLayoutParamsBasedOnCurrentWindowLayoutParams()

        if (Log.IS_DEBUG) {
            val w = windowLayoutParams.width
            val h = windowLayoutParams.height
            Log.logDebug(TAG, "updateWindowParams() : WinSize WxH = $w x $h")
        }
    }

    private fun updateWindowOpenCloseLayoutParamsBasedOnCurrentWindowLayoutParams() {
        openedWindowLayout.x = 0
        openedWindowLayout.y = windowLayoutParams.y
        openedWindowLayout.width = windowLayoutParams.width
        openedWindowLayout.height = windowLayoutParams.height

        closedWindowLayout.x = -1 * (windowLayoutParams.width - SLIDER_GRIP_WIDTH_PIX)
        closedWindowLayout.y = windowLayoutParams.y
        closedWindowLayout.width = windowLayoutParams.width
        closedWindowLayout.height = SLIDER_GRIP_HEIGHT_PIX
    }

    private fun updateLayoutParams() {
        val containerParams = web_frame_container.layoutParams
        containerParams.width = windowLayoutParams.width
        containerParams.height = windowLayoutParams.height
        web_frame_container.layoutParams = containerParams
    }

    private fun updateTotalUserInterface() {
        updateDisplayConfig()
        updateWindowParams()
        updateLayoutParams()

        if (isAttachedToWindow) {
            val containerParams = web_frame_container.layoutParams

            // Layout.
            if (windowLayoutParams.flags == INTERACTIVE_WINDOW_FLAGS) {
                // Opened state.
                containerParams.height = openedWindowLayout.height
                windowLayoutParams.x = openedWindowLayout.x
                windowLayoutParams.height = openedWindowLayout.height
            } else {
                // Closed or Hidden state.

                containerParams.height = closedWindowLayout.height
                windowLayoutParams.x = closedWindowLayout.x
                windowLayoutParams.height = closedWindowLayout.height

                if (!topWebFrame.isActive()) {
                    // Hidden state.
                    windowLayoutParams.x = WINDOW_HIDDEN_POS_X
                }
            }

            // Update layout.
            web_frame_container.layoutParams = containerParams
            windowManager.updateViewLayout(this, windowLayoutParams)

        } else {
            // Will be added to window manager as Opened state.
            windowLayoutParams.x = openedWindowLayout.x
        }
    }

    /**
     * Toggle WebView window is on screen or not.
     * If WebView window is not on screen even slider grip, WebView is paused.
     * In other words, even if WebView window is closed, WebView is resumed.
     */
    fun toggleShowHide() {
        when {
            windowLayoutParams.x == WINDOW_HIDDEN_POS_X -> {
                // Hidden -> Closed.
                windowLayoutParams.x = closedWindowLayout.x

                webFrames.forEach(WebFrame::onResume)
            }

            windowLayoutParams.x == closedWindowLayout.x -> {
                // Closed -> Hidden.
                windowLayoutParams.x = WINDOW_HIDDEN_POS_X

                webFrames.forEach(WebFrame::onPause)
            }

            else -> {
                // NOP. Maybe now on Opened.
                return
            }
        }

        windowManager.updateViewLayout(this, windowLayoutParams)
    }

    public override fun onConfigurationChanged(newConfig: Configuration) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onConfigurationChanged() : Config=$newConfig")
        super.onConfigurationChanged(newConfig)

        // Update UI.
        updateTotalUserInterface()
    }

    private inner class WebFrameCallbackImpl : WebFrame.Callback {
        private val TAG = "WebFrameCallbackImpl"

        private var onDownWinPosX = 0

        override fun onTabClicked() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onTabClicked()")

            // NOP.

        }

        override fun onSlideWindowStarted(startedRawPos: Point) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSlideWindowStarted()")

            // Remove old task
            windowStateConvergentTask?.let {
                App.ui.removeCallbacks(it)
            }

            onDownWinPosX = windowLayoutParams.x

            // Disable resizer.
            resizer_grip.visibility = INVISIBLE

        }

        override fun onSlideWindowOnGoing(startedRawPos: Point, diffPos: Point) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSlideWindowOnGoing()")

            val nextWinPosX = onDownWinPosX + diffPos.x

            // Check limit.
            if (nextWinPosX in closedWindowLayout.x..openedWindowLayout.x) {
                windowLayoutParams.x = nextWinPosX
                windowManager.updateViewLayout(this@WebViewWindowRootView, windowLayoutParams)
            }
        }

        override fun onSlideWindowStopped(startedRawPos: Point, diffPos: Point, stoppedRawPos: Point) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSlideWindowStopped()")

            val targetLayout: LayoutRect

            if (0 < diffPos.x) { // Open direction.
                val openThreshold = displaySize.width / 3
                if (openThreshold < stoppedRawPos.x) { // Do open.
                    targetLayout = openedWindowLayout
                    windowLayoutParams.flags = INTERACTIVE_WINDOW_FLAGS
                } else { // Stay closed.
                    targetLayout = closedWindowLayout
                    windowLayoutParams.flags = NOT_INTERACTIVE_WINDOW_FLAGS
                }
            } else { // Close direction.
                val closeThreshold = displaySize.width * 2 / 3
                if (stoppedRawPos.x < closeThreshold) { // Do close.
                    targetLayout = closedWindowLayout
                    windowLayoutParams.flags = NOT_INTERACTIVE_WINDOW_FLAGS
                } else { // Stay opened.
                    targetLayout = openedWindowLayout
                    windowLayoutParams.flags = INTERACTIVE_WINDOW_FLAGS
                }
            }

            // Start fix.
            WindowStateConvergentTask(targetLayout).let {
                App.ui.post(it)
                windowStateConvergentTask = it
            }

            // Reset.
            onDownWinPosX = 0
        }
    }

    private inner class ResizerGripTouchEventHandler : OnTouchListener {
        private var onDownWinFlexLineSize = 0
        private var onDownLayoutFlexLineSize = 0
        private var onDownBasePosit = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    when (displayOrientation) {
                        Orientation.PORTRAIT -> {
                            onDownWinFlexLineSize = windowLayoutParams.height
                            onDownLayoutFlexLineSize = web_frame_container.layoutParams.height
                            onDownBasePosit = event.rawY.toInt()
                        }
                        Orientation.LANDSCAPE -> {
                            onDownWinFlexLineSize = windowLayoutParams.width
                            onDownLayoutFlexLineSize = web_frame_container.layoutParams.width
                            onDownBasePosit = event.rawX.toInt()
                        }
                    }

                    // Hide grip during window resizing.
                    webFrames.forEach(WebFrame::hideGrip)
                }

                MotionEvent.ACTION_MOVE -> {
                    when (displayOrientation) {
                        Orientation.PORTRAIT -> {
                            val diff = event.rawY.toInt() - onDownBasePosit
                            val maxLimit = displaySize.height - openedWindowLayout.y - statusBarSize
                            val newWinFlexLineSize = onDownWinFlexLineSize + diff
                            val newLayoutFlexLineSize = onDownLayoutFlexLineSize + diff

                            if (newWinFlexLineSize in MIN_WINDOW_SIZE..maxLimit) {
                                val layoutParams = web_frame_container.layoutParams
                                layoutParams.height = newLayoutFlexLineSize
                                windowLayoutParams.height = newWinFlexLineSize

                                // Update layout size.
                                web_frame_container.layoutParams = layoutParams
                                windowManager.updateViewLayout(
                                        this@WebViewWindowRootView,
                                        windowLayoutParams)
                            }
                        }
                        Orientation.LANDSCAPE -> {
                            val diff = event.rawX.toInt() - onDownBasePosit
                            val maxLimit = displaySize.width - openedWindowLayout.x
                            val newWinFlexLineSize = onDownWinFlexLineSize + diff
                            val newLayoutFlexLineSize = onDownLayoutFlexLineSize + diff

                            if (newWinFlexLineSize in MIN_WINDOW_SIZE..maxLimit) {
                                val layoutParams = web_frame_container.layoutParams
                                layoutParams.width = newLayoutFlexLineSize
                                windowLayoutParams.width = newWinFlexLineSize

                                // Update layout size.
                                web_frame_container.layoutParams = layoutParams
                                windowManager.updateViewLayout(
                                        this@WebViewWindowRootView,
                                        windowLayoutParams)
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Update opened window layout parameters.
                    updateWindowOpenCloseLayoutParamsBasedOnCurrentWindowLayoutParams()

                    // Reset.
                    onDownWinFlexLineSize = 0
                    onDownLayoutFlexLineSize = 0
                    onDownBasePosit = 0

                    webFrames.forEach(WebFrame::showGrip)
                }

                else -> {
                    // NOP. Unexpected.
                }
            }

            return true
        }
    }

    private inner class WindowStateConvergentTask(
            private val targetWindowLayout: LayoutRect) : Runnable {
        private val TAG = "WindowPositionCorrectionTask"

        // Proportional gain.
        private val P_GAIN = 0.2f

        // Animation refresh interval.
        private val WINDOW_ANIMATION_INTERVAL_MILLIS = 16

        // Last delta.
        private var lastDeltaX = 0
        private var lastDeltaY = 0
        private var lastDeltaH = 0

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            val dX = targetWindowLayout.x - windowLayoutParams.x
            val dY = targetWindowLayout.y - windowLayoutParams.y

            // Update layout.
            windowLayoutParams.x += (dX * P_GAIN).toInt()
            windowLayoutParams.y += (dY * P_GAIN).toInt()

            if (this@WebViewWindowRootView.isAttachedToWindow) {
                windowManager.updateViewLayout(
                        this@WebViewWindowRootView,
                        windowLayoutParams)
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
                windowLayoutParams.x = targetWindowLayout.x
                windowLayoutParams.y = targetWindowLayout.y

                // On opened.
                if (targetWindowLayout == openedWindowLayout) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "on Opened.")

                    val layoutParams = web_frame_container.layoutParams

                    // Check window is fully expanded or not.
                    if (layoutParams.height == openedWindowLayout.height) {
                        // OK, Expansion is done.
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "Expansion DONE.")

                        // Enable resizer.
                        resizer_grip.visibility = VISIBLE

                        return
                    } else {
                        // NG. Update window and layout height to expand.
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "Expansion in progress.")

                        if (windowLayoutParams.height != openedWindowLayout.height) {
                            // At first of window expansion, fix window size in advance,
                            // after then, expand inner layout size with animation.
                            if (Log.IS_DEBUG) Log.logDebug(TAG, "Expand window size in advance.")

                            layoutParams.height = closedWindowLayout.height
                            web_frame_container.layoutParams = layoutParams

                            windowLayoutParams.height = openedWindowLayout.height
                            windowManager.updateViewLayout(
                                    this@WebViewWindowRootView,
                                    windowLayoutParams)
                        }

                        val diff = openedWindowLayout.height - layoutParams.height

                        if (diff == lastDeltaH) {
                            // Consider layout is already fully expanded.
                            layoutParams.height = openedWindowLayout.height
                        } else {
                            // Expansion in progress.
                            layoutParams.height += (diff * P_GAIN).toInt()
                        }
                        web_frame_container.layoutParams = layoutParams

                        lastDeltaH = diff

                        if (Log.IS_DEBUG) Log.logDebug(TAG, "LayoutH = ${layoutParams.height}")
                    }
                }

                // On closed.
                if (targetWindowLayout == closedWindowLayout) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "on Closed.")

                    windowLayoutParams.height = closedWindowLayout.height
                    windowManager.updateViewLayout(
                            this@WebViewWindowRootView,
                            windowLayoutParams)

                    web_frame_container.layoutParams.height = closedWindowLayout.height

                    return
                }
            }

            lastDeltaX = dX
            lastDeltaY = dY

            // Next.
            App.ui.postDelayed(this, WINDOW_ANIMATION_INTERVAL_MILLIS.toLong())

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }

    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                when (event.action) {
                    KeyEvent.ACTION_UP -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "KEYCODE_BACK : UP")

                        // Go back on WebView.
                        if (topWebFrame.canGoBack()) {
                            topWebFrame.goBack()
                        }
                    }
                }

                return true
            }
        }

        // Fall back to default.
        return super.dispatchKeyEvent(event)
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

        // Window size.
        private const val MIN_WINDOW_SIZE = 256

        // Hidden window position constants.
        private const val WINDOW_HIDDEN_POS_X = -5000

        @SuppressLint("InflateParams")
        fun inflate(context: Context): WebViewWindowRootView {
            return LayoutInflater.from(context).inflate(
                    R.layout.overlay_root_view,
                    null) as WebViewWindowRootView
        }
    }
}
