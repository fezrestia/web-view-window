@file:Suppress("ConstantConditionIf", "PrivatePropertyName")

package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Message
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
import com.fezrestia.android.webviewwindow.control.WebViewWindowController
import kotlinx.android.synthetic.main.overlay_root_view.view.*
import kotlin.math.abs
import kotlin.math.max

class WebViewWindowRootView(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int) : FrameLayout(context, attrs, defStyle) {

    var controller: WebViewWindowController? = null

    // Grip size.
    private val SLIDER_GRIP_WIDTH_PIX = resources.getDimensionPixelSize(R.dimen.grip_width)
    private val SLIDER_GRIP_HEIGHT_PIX = resources.getDimensionPixelSize(R.dimen.grip_height)
    // Icon size.
    private val RIGHT_BOTTOM_ICON_SIZE_PIX = resources.getDimensionPixelSize(R.dimen.right_bottom_icon_size)

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

        resizer_grip.setOnTouchListener(ResizerGripTouchEventHandler())

        add_new_web_frame_button.setOnClickListener(AddNewWebFrameButtonOnClickListenerImpl())

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                INTERACTIVE_WINDOW_FLAGS, // Initialize as opened state.
                PixelFormat.TRANSLUCENT)

        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : X")
    }

    /**
     * Release all resources.
     */
    fun release() {
        resizer_grip.setOnTouchListener(null)
        add_new_web_frame_button.setOnClickListener(null)
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

        val frames = webFrames.toTypedArray()
        frames.forEach { frame ->
            removeWebFrame(frame)
        }
    }

    /**
     * Add new WebFrame to tail of stack with default URL.
     */
    fun addNewWebFrameWithDefaultUrl() {
        var baseUrl = App.sp.getString(
                Constants.SP_KEY_BASE_LOAD_URL,
                Constants.DEFAULT_BASE_LOAD_URL) as String
        if (baseUrl.isEmpty()) {
            baseUrl = Constants.DEFAULT_BASE_LOAD_URL
        }

        addNewWebFrame(baseUrl, null)
    }

    /**
     * Add new WebFrame to tail of stack with Transport message.
     */
    fun addNewWebFrameWithTransportMsg(msg: Message) {
        addNewWebFrame(null, msg)
    }

    private fun addNewWebFrame(url: String?, msg: Message?) {
        val newWebFrame = WebFrame.inflate(context)
        newWebFrame.initialize(WebFrameCallbackImpl(), right_bottom_icon_container.height)

        webFrames.add(newWebFrame)
        web_frame_container.addView(newWebFrame)

        if (url != null) {
            newWebFrame.loadUrl(url)
        }
        if (msg != null) {
            newWebFrame.loadMsg(msg)
        }

        // Add new frame as top.
        topWebFrame = newWebFrame

        updateGripState()
    }

    private fun updateGripState() {
        val topIndex = webFrames.indexOf(topWebFrame)

        webFrames.forEach { webFrame ->
            // Grip position and selected or not.
            val index = webFrames.indexOf(webFrame)
            val isTop = topIndex == index
            webFrame.setFrameOrder(index, webFrames.size, isTop)

            // Z-Order. Top frame elevation = 0, and other frames is -0.1 x index diff from top.
            val diff = abs(topIndex - webFrames.indexOf(webFrame))
            webFrame.elevation = -0.1f * diff
        }
    }

    private fun closeWebFrame(webFrame: WebFrame) {
        val order = webFrames.indexOf(webFrame)
        removeWebFrame(webFrame)
        val nextTopOrder = max(0, order - 1)
        topWebFrame = webFrames[nextTopOrder]

        updateGripState()
    }

    private fun removeWebFrame(webFrame: WebFrame) {
        web_frame_container.removeView(webFrame)
        webFrames.remove(webFrame)
        webFrame.release()
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
        closedWindowLayout.height = SLIDER_GRIP_HEIGHT_PIX + (SLIDER_GRIP_HEIGHT_PIX / 2) + (RIGHT_BOTTOM_ICON_SIZE_PIX * 2)
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

    fun startSlideInWindow() {
        // Remove old task
        windowStateConvergentTask?.let {
            App.ui.removeCallbacks(it)
        }
        // Auto open overlay window.
        WindowStateConvergentTask(openedWindowLayout).let {
            App.ui.post(it)
            windowStateConvergentTask = it
        }
    }

    fun startSlideOutWindow() {
        // Remove old task
        windowStateConvergentTask?.let {
            App.ui.removeCallbacks(it)
        }
        // Auto close overlay window.
        WindowStateConvergentTask(closedWindowLayout).let {
            App.ui.post(it)
            windowStateConvergentTask = it
        }
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

        override fun onTabClicked(frameOrder: Int) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onTabClicked() : frameOrder=$frameOrder")

            topWebFrame = webFrames[frameOrder]

            updateGripState()
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
            add_new_web_frame_button.visibility = INVISIBLE
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

            targetLayout = if (0 < diffPos.x) { // Open direction.
                val openThreshold = displaySize.width / 3
                if (openThreshold < stoppedRawPos.x) { // Do open.
                    openedWindowLayout
                } else { // Stay closed.
                    closedWindowLayout
                }
            } else { // Close direction.
                val closeThreshold = displaySize.width * 2 / 3
                if (stoppedRawPos.x < closeThreshold) { // Do close.
                    closedWindowLayout
                } else { // Stay opened.
                    openedWindowLayout
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

        override fun onOpenNewWindowRequested(msg: Message) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOpenNewWindowRequested()")

            addNewWebFrameWithTransportMsg(msg)
        }

        override fun onCloseRequired(frameOrder: Int) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCloseRequired() : frameOrder = $frameOrder")

            if (webFrames.size == 1) {
                // This frame is LAST one. Do not close it.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## This WebFrame is LAST one. Do not close.")
                return
            }

            closeWebFrame(webFrames[frameOrder])
        }

        override fun onStartChromeCustomTabRequired(url: String) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartChromeCustomTabRequired() : url=$url")

            controller?.startChromeCustomTab(url)

            startSlideOutWindow()
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

    private inner class AddNewWebFrameButtonOnClickListenerImpl : OnClickListener {
        private val TAG = "AddNewWebFrameButtonOnClickListenerImpl"

        override fun onClick(v: View) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onClick()")
            addNewWebFrameWithDefaultUrl()
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

        init {
            when (targetWindowLayout.height) {
                openedWindowLayout.height -> {
                    windowLayoutParams.flags = INTERACTIVE_WINDOW_FLAGS
                }
                closedWindowLayout.height -> {
                    windowLayoutParams.flags = NOT_INTERACTIVE_WINDOW_FLAGS
                }
                else -> throw RuntimeException("Unexpected target = $targetWindowLayout")
            }
        }

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

            // Check open/close animation is done or not.
            if (lastDeltaX == dX && lastDeltaY == dY) {
                // Correction is already convergent.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already position fixed.")

                // Fix position.
                windowLayoutParams.x = targetWindowLayout.x
                windowLayoutParams.y = targetWindowLayout.y

                // Expand/Collapse animation.
                val layoutParams = web_frame_container.layoutParams
                // Check window is fully expanded/collapsed or not.
                if (layoutParams.height == targetWindowLayout.height) {
                    // OK, Animation is already converged.

                    when (layoutParams.height) {
                        openedWindowLayout.height -> {
                            // OK, Expanding is done.
                            if (Log.IS_DEBUG) Log.logDebug(TAG, "Expanding DONE.")

                            // Enable right-bottom icons.
                            resizer_grip.visibility = VISIBLE
                            add_new_web_frame_button.visibility = VISIBLE

                            return // Exit task.
                        }

                        closedWindowLayout.height -> {
                            // OK, Collapsing is done.
                            if (Log.IS_DEBUG) Log.logDebug(TAG, "Collapsing DONE.")

                            // For collapse animation, fix window size at last,
                            if (Log.IS_DEBUG) Log.logDebug(TAG, "Collapse window size at last.")

                            // Animation final height.
                            layoutParams.height = closedWindowLayout.height
                            web_frame_container.layoutParams = layoutParams

                            // Fix window size.
                            windowLayoutParams.height = closedWindowLayout.height
                            windowManager.updateViewLayout(
                                    this@WebViewWindowRootView,
                                    windowLayoutParams)

                            // Disable right-bottom icons.
                            resizer_grip.visibility = INVISIBLE
                            add_new_web_frame_button.visibility = INVISIBLE

                            return // Exit task.
                        }

                        else -> throw RuntimeException("Unexpected target = $targetWindowLayout")
                    }
                } else {
                    // NG. Update window and layout height to expand/collapse.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Expansion in progress.")

                    // For expanding animation, fix window size in advance,
                    // after then, expand inner layout size with animation.
                    if (layoutParams.height == closedWindowLayout.height) {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "Expand window size in advance.")

                        // Animation initial height.
                        layoutParams.height = closedWindowLayout.height
                        web_frame_container.layoutParams = layoutParams

                        // Fix window size.
                        windowLayoutParams.height = openedWindowLayout.height
                        windowManager.updateViewLayout(
                                this@WebViewWindowRootView,
                                windowLayoutParams)
                    }

                    val diff = targetWindowLayout.height - layoutParams.height

                    if (diff == lastDeltaH) {
                        // Consider layout is already fully expanded/collapsed.
                        layoutParams.height = targetWindowLayout.height
                    } else {
                        // Expansion in progress.
                        layoutParams.height += (diff * P_GAIN).toInt()
                    }
                    web_frame_container.layoutParams = layoutParams

                    lastDeltaH = diff

                    if (Log.IS_DEBUG) Log.logDebug(TAG, "LayoutH = ${layoutParams.height}")
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
                            if (Log.IS_DEBUG) Log.logDebug(TAG, "## Go back")
                            topWebFrame.goBack()
                        } else {
                            // Already first page.

                            if (webFrames.size != 1) {
                                // This frame is NOT last one.
                                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Remove current WebFrame")

                                closeWebFrame(topWebFrame)

                            } else {
                                // NOP. This is last one frame. Do NOT remove this.
                                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Do NOT remove last one")
                            }
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
