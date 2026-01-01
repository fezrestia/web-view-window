@file:Suppress("ConstantConditionIf", "PrivatePropertyName")

package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

import com.fezrestia.android.webviewwindow.App
import com.fezrestia.android.util.LayoutRect
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.R
import kotlin.math.abs
import kotlin.math.max

class WebViewWindowRootView(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int) : FrameLayout(context, attrs, defStyle) {

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

    // Configs.
    private val willCollapseOnClosed = App.sp.getBoolean(Constants.SP_KEY_WILL_COLLAPSE_ON_CLOSED, true)

    // Web frames.
    private val webFrames: MutableList<WebFrame> = mutableListOf()

    // Current top WebFrame.
    private lateinit var topWebFrame: WebFrame

    // View elements.
    private val web_frame_container: FrameLayout
        get() {
            return this.findViewById(R.id.web_frame_container)
        }
    private val right_bottom_icon_container: LinearLayout
        get() {
            return this.findViewById(R.id.right_bottom_icon_container)
        }
    private val resizer_grip: ImageView
        get() {
            return this.findViewById(R.id.resizer_grip)
        }
    private val add_new_web_frame_button: ImageView
        get() {
            return this.findViewById(R.id.add_new_web_frame_button)
        }

    // UI event callback interface.
    interface Callback {
        fun onNewWebFrameRequired()
        fun onFullBrowserRequired(url: String)
        fun onSaveStateRequired()
        fun onWakeLockAcquireRequested()
        fun onWakeLockReleaseRequested()
    }
    private var callback: Callback? = null

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
     *
     * @param callback
     */
    fun initialize(callback: Callback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : E")

        this.callback = callback

        resizer_grip.setOnTouchListener(ResizerGripTouchEventHandler())

        add_new_web_frame_button.setOnClickListener(AddNewWebFrameButtonOnClickListenerImpl())

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                NOT_INTERACTIVE_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT)

        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : X")
    }

    /**
     * Release all resources.
     */
    fun release() {
        callback = null
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
     * Add empty new WebFrame to tail of stack.
     */
    fun addEmptyWebFrameToTail(defaultUrl: String) {
        val targetIndex = webFrames.lastIndex + 1 // Add to tail end.
        addNewWebFrame(null, null, null, defaultUrl, targetIndex)
    }

    /**
     * Add new WebFrame to tail of stack with Transport message.
     */
    fun addNewWebFrameUnderTopWithTransportMsg(msg: Message) {
        val targetIndex = webFrames.indexOf(topWebFrame) + 1
        addNewWebFrame(null, msg, null, null, targetIndex)
    }

    /**
     * Add new WebFrame to tail of stack with state Bundle.
     */
    fun addNewWebFrameToTailWithState(state: Bundle) {
        val targetIndex = webFrames.lastIndex + 1 // Add to tail end.
        addNewWebFrame(null, null, state, null, targetIndex)
    }

    @Suppress("SameParameterValue")
    private fun addNewWebFrame(
            url: String?,
            msg: Message?,
            state: Bundle?,
            defaultUrl: String?,
            targetIndex: Int) {
        val newWebFrame = WebFrame.inflate(context)
        newWebFrame.initialize(
                WebFrameCallbackImpl(),
                web_frame_container,
                right_bottom_icon_container,
                defaultUrl)

        webFrames.add(targetIndex, newWebFrame)
        web_frame_container.addView(newWebFrame)

        if (url != null) {
            newWebFrame.loadUrl(url)
        }
        if (msg != null) {
            newWebFrame.loadMsg(msg)
        }
        if (state != null) {
            newWebFrame.loadState(state)
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
        displaySize = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val size = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(size)
            LayoutRect(0, 0, size.x, size.y)
        } else {
            val rect = windowManager.currentWindowMetrics.bounds
            LayoutRect(0, 0, rect.width(), rect.height())
        }

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

                windowLayoutParams.y = 0
            }

            Orientation.LANDSCAPE -> {
                windowLayoutParams.width = (displaySize.longLine * SCREEN_LONG_LINE_CLEARANCE).toInt()
                windowLayoutParams.height = displaySize.shortLine - statusBarSize * 2

                windowLayoutParams.y = statusBarSize / 4
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
        if (willCollapseOnClosed) {
            closedWindowLayout.height = SLIDER_GRIP_HEIGHT_PIX * 2 + RIGHT_BOTTOM_ICON_SIZE_PIX * 2
        } else {
            // Target position of open/close is detected by height value.
            // so, different between open/close is necessary.
            closedWindowLayout.height = windowLayoutParams.height - 1
        }
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
            if (isHidden()) {
                // Closed or Hidden state.

                containerParams.height = closedWindowLayout.height
                windowLayoutParams.x = closedWindowLayout.x
                windowLayoutParams.height = closedWindowLayout.height

                if (!topWebFrame.isActive()) {
                    // Hidden state.
                    windowLayoutParams.x = WINDOW_HIDDEN_POS_X
                }
            } else {
                // Opened state.
                containerParams.height = openedWindowLayout.height
                windowLayoutParams.x = openedWindowLayout.x
                windowLayoutParams.height = openedWindowLayout.height
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
     *
     * @return Boolean Show/Hide state is changed or not.
     */
    fun toggleShowHide(): Boolean {
        when (windowLayoutParams.x) {
            WINDOW_HIDDEN_POS_X -> {
                windowStateConvergentTask?.forceFinish()

                // Hidden -> Closed.
                windowLayoutParams.x = closedWindowLayout.x

                webFrames.forEach(WebFrame::onResume)
            }

            closedWindowLayout.x -> {
                windowStateConvergentTask?.forceFinish()

                // Closed -> Hidden.
                windowLayoutParams.x = WINDOW_HIDDEN_POS_X

                webFrames.forEach(WebFrame::onPause)
            }

            else -> {
                // NOP. Maybe now on Opened. Wakelock acquire/release is not necessary.
                return false
            }
        }

        windowManager.updateViewLayout(this, windowLayoutParams)

        return true
    }

    /**
     * Window is hidden position or not.
     *
     * @return
     */
    fun isHidden(): Boolean {
        return windowLayoutParams.x == WINDOW_HIDDEN_POS_X
    }

    fun startSlideInWindow() {
        // Remove old task
        windowStateConvergentTask?.let {
            App.ui.removeCallbacks(it)
        }
        // Auto open overlay window.
        WindowStateConvergentTask(openedWindowLayout, ALPHA_OPEN).let {
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
        WindowStateConvergentTask(closedWindowLayout, ALPHA_CLOSE).let {
            App.ui.post(it)
            windowStateConvergentTask = it
        }
    }

    fun getWebViewStates(): List<Bundle> {
        return webFrames.map { webFrame ->
            webFrame.getWebViewState()
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
            val targetAlpha: Float
            if (0 < diffPos.x) { // Open direction.
                val openThreshold = displaySize.width / 3
                if (openThreshold < stoppedRawPos.x) { // Do open.
                    targetLayout = openedWindowLayout
                    targetAlpha = ALPHA_OPEN
                } else { // Stay closed.
                    targetLayout = closedWindowLayout
                    targetAlpha = ALPHA_CLOSE
                }
            } else { // Close direction.
                val closeThreshold = displaySize.width * 2 / 3
                if (stoppedRawPos.x < closeThreshold) { // Do close.
                    targetLayout = closedWindowLayout
                    targetAlpha = ALPHA_CLOSE
                } else { // Stay opened.
                    targetLayout = openedWindowLayout
                    targetAlpha = ALPHA_OPEN
                }
            }

            // Start fix.
            WindowStateConvergentTask(targetLayout, targetAlpha).let {
                App.ui.post(it)
                windowStateConvergentTask = it
            }

            // Reset.
            onDownWinPosX = 0
        }

        override fun onOpenNewWindowRequested(msg: Message) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOpenNewWindowRequested()")

            addNewWebFrameUnderTopWithTransportMsg(msg)
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

            callback?.onFullBrowserRequired(url)

            startSlideOutWindow()
        }

        override fun onUrlChanged(url: String) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onUrlChanged() : url=$url")

            callback?.onSaveStateRequired()
        }

        override fun onToggleFocusRequested() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onToggleFocusRequested()")

            if (windowLayoutParams.flags == NOT_INTERACTIVE_WINDOW_FLAGS) {
                windowLayoutParams.flags = INTERACTIVE_WINDOW_FLAGS

                // Enable dim behind during gain focus.
                windowLayoutParams.flags += WindowManager.LayoutParams.FLAG_DIM_BEHIND
                windowLayoutParams.dimAmount = 0.6f
            } else {
                windowLayoutParams.flags = NOT_INTERACTIVE_WINDOW_FLAGS
            }

            windowManager.updateViewLayout(
                this@WebViewWindowRootView,
                windowLayoutParams)
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
            callback?.onNewWebFrameRequired()
        }
    }

    private inner class WindowStateConvergentTask(
            private val targetWindowLayout: LayoutRect,
            private val targetAlpha: Float) : Runnable {
        private val TAG = "WindowPositionCorrectionTask"

        // Proportional gain.
        private val P_GAIN = 0.2f

        // Animation refresh interval.
        private val WINDOW_ANIMATION_INTERVAL_MILLIS = 16

        // Last delta.
        private var lastDeltaX = 0
        private var lastDeltaY = 0
        private var lastDeltaH = 0
        private var lastDeltaAlpha = 0.0f

        init {
            // Always release focus on open/close overlay.
            windowLayoutParams.flags = NOT_INTERACTIVE_WINDOW_FLAGS
        }

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            val dX = targetWindowLayout.x - windowLayoutParams.x
            val dY = targetWindowLayout.y - windowLayoutParams.y
            val dA = targetAlpha - windowLayoutParams.alpha

            // Update layout.
            windowLayoutParams.x += (dX * P_GAIN).toInt()
            windowLayoutParams.y += (dY * P_GAIN).toInt()
            windowLayoutParams.alpha += (dA * P_GAIN)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : Updated windowLayoutParams.x/y = [${windowLayoutParams.x}/${windowLayoutParams.y}]")

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
            val isSameAlpha = (lastDeltaAlpha * 100.0f).toInt() == (dA * 100.0f).toInt()
            if (lastDeltaX == dX && lastDeltaY == dY && isSameAlpha) {
                // Correction is already convergent.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already position/alpha fixed.")

                // Fix position/alpha.
                windowLayoutParams.x = targetWindowLayout.x
                windowLayoutParams.y = targetWindowLayout.y
                windowLayoutParams.alpha = targetAlpha
                if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : Fixed windowLayoutParams.x/y = [${windowLayoutParams.x}/${windowLayoutParams.y}]")

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

                            // Request to acquire WakeLock.
                            callback?.onWakeLockAcquireRequested()

                            return // Exit task.
                        }

                        closedWindowLayout.height -> {
                            // OK, Collapsing is done.
                            // For collapse animation, fix window size at last,
                            if (Log.IS_DEBUG) Log.logDebug(TAG, "Collapsing DONE.")

                            // Animation final height.
                            layoutParams.height = closedWindowLayout.height
                            web_frame_container.layoutParams = layoutParams

                            // Disable right-bottom icons.
                            resizer_grip.visibility = INVISIBLE
                            add_new_web_frame_button.visibility = INVISIBLE

                            // Fix window size.
//                            windowLayoutParams.height = closedWindowLayout.height
//                            windowManager.updateViewLayout(
//                                this@WebViewWindowRootView,
//                                windowLayoutParams)

                            // Request to release WakeLock
                            callback?.onWakeLockReleaseRequested()

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

                        // Fix window size.
//                        windowLayoutParams.height = openedWindowLayout.height
//                        windowManager.updateViewLayout(
//                                this@WebViewWindowRootView,
//                                windowLayoutParams)
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
            lastDeltaAlpha = dA

            // Next.
            App.ui.postDelayed(this, WINDOW_ANIMATION_INTERVAL_MILLIS.toLong())

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }

        fun forceFinish() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "forceFinish() : E")

            // Stop window animation immediately.
            App.ui.removeCallbacks(this)

            // Fix position/alpha.
            windowLayoutParams.x = targetWindowLayout.x
            windowLayoutParams.y = targetWindowLayout.y
            windowLayoutParams.width = targetWindowLayout.width
            windowLayoutParams.height = targetWindowLayout.height
            windowLayoutParams.alpha = targetAlpha

            // Expand/Collapse animation.
            val layoutParams = web_frame_container.layoutParams

            when (targetWindowLayout.height) {
                // Expand direction.
                openedWindowLayout.height -> {
                    // Web view stack layout.
                    layoutParams.height = openedWindowLayout.height
                    web_frame_container.layoutParams = layoutParams

                    resizer_grip.visibility = VISIBLE
                    add_new_web_frame_button.visibility = VISIBLE

                    // Request to acquire WakeLock.
                    callback?.onWakeLockAcquireRequested()
                }

                // Collapse direction.
                closedWindowLayout.height -> {
                    // Web view stack layout.
                    layoutParams.height = closedWindowLayout.height
                    web_frame_container.layoutParams = layoutParams

                    // Disable right-bottom icons.
                    resizer_grip.visibility = INVISIBLE
                    add_new_web_frame_button.visibility = INVISIBLE

                    // Request to release WakeLock.
                    callback?.onWakeLockReleaseRequested()
                }
            }

            // Fix window size.
            windowManager.updateViewLayout(
                    this@WebViewWindowRootView,
                    windowLayoutParams)

            if (Log.IS_DEBUG) Log.logDebug(TAG, "forceFinish() : X")
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
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )

        private const val ALPHA_OPEN = 1.0f
        private const val ALPHA_CLOSE = 0.5f

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
