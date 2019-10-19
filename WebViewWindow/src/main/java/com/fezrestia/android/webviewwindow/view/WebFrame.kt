@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Message
import android.util.AttributeSet
import android.view.*
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.R
import kotlinx.android.synthetic.main.web_frame.view.*
import kotlin.math.abs
import kotlin.math.min

class WebFrame(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int) : LinearLayout(context, attrs, defStyle) {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val TOUCH_SLOP: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val SLIDER_GRIP_HEIGHT_PIX = resources.getDimensionPixelSize(R.dimen.grip_height)

    private var callback: Callback? = null

    private var frameOrder: Int = 0
    private var totalFrameCount: Int = 0

    /**
     * WebFrame related event callback interface.
     */
    interface Callback {
        fun onTabClicked(frameOrder: Int)

        fun onSlideWindowStarted(startedRawPos: Point)
        fun onSlideWindowOnGoing(startedRawPos: Point, diffPos: Point)
        fun onSlideWindowStopped(startedRawPos: Point, diffPos: Point, stoppedRawPos: Point)

        fun onOpenNewWindowRequested(msg: Message)
    }

    private inner class ExtendedWebViewCallbackImpl : ExtendedWebView.Callback {
        override fun onNewWindowRequested(msg: Message) {
            callback?.onOpenNewWindowRequested(msg)
        }
    }

    /**
     * Initialize WebFrame.
     *
     * @param callback
     */
    fun initialize(callback: Callback) {
        this.callback = callback

        // Web view.
        web_view.initialize(ExtendedWebViewCallbackImpl())
        web_view.onResume()

        // Slider grip.
        slider_grip.setOnTouchListener(SliderGripTouchEventListenerImpl())

        // Per-layout process.
        viewTreeObserver.addOnGlobalLayoutListener(LayoutObserverImpl())
    }

    /**
     * Start loading URL.
     *
     * @param url
     */
    fun loadUrl(url: String) {
        web_view.loadUrl(url)
    }

    /**
     * Start loading with WebViewTransport message.
     *
     * @param msg
     */
    fun loadMsg(msg: Message) {
        val transport = msg.obj as WebView.WebViewTransport
        transport.webView = web_view
        msg.sendToTarget()
    }

    /**
     * Release ALL references.
     */
    fun release() {
        callback = null
        slider_grip.setOnTouchListener(null)
        web_view.release()
    }

    private inner class SliderGripTouchEventListenerImpl : OnTouchListener {
        private var onDownRawX = 0
        private var onDownRawY = 0
        private var isDragging = false

        private fun diffX(curX: Int): Int {
            return curX - onDownRawX
        }

        private fun diffY(curY: Int): Int {
            return curY - onDownRawY
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val curX = event.rawX.toInt()
            val curY = event.rawY.toInt()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    onDownRawX = curX
                    onDownRawY = curY
                }

                MotionEvent.ACTION_MOVE -> {
                    // Detect finger starts moving or not.
                    if (isDragging) {
                        // Drag on going.
                        callback?.onSlideWindowOnGoing(
                                Point(onDownRawX, onDownRawY),
                                Point(diffX(curX), diffY(curY)))
                    } else {
                        // Finger still stayed yet.
                        if (TOUCH_SLOP < abs(diffX(curX))) {
                            // Drag is started.

                            isDragging = true

                            callback?.onSlideWindowStarted(Point(curX, curY))

                            // Update touch down pos to smooth drag starting. (to start diff from 0)
                            onDownRawX = curX
                            onDownRawY = curY
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        // Drag end.
                        callback?.onSlideWindowStopped(
                                Point(onDownRawX, onDownRawY),
                                Point(diffX(curX), diffY(curY)),
                                Point(curX, curY))
                    } else {
                        // Not dragged. Maybe clicked.
                        if (abs(diffX(curX)) < TOUCH_SLOP && abs(diffY(curY)) < TOUCH_SLOP) {
                            performClick()
                            callback?.onTabClicked(frameOrder)
                        }
                    }

                    // Reset.
                    onDownRawX = 0
                    onDownRawY = 0
                    isDragging = false
                }
            }

            return true
        }
    }

    /**
     * Order of this WebFrame.
     *
     * @param order 0 means top WebFrame.
     * @param total Count of WebFrames.
     */
    fun setFrameOrder(order: Int, total: Int) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "setFrameOrder() : order=$order, total=$total")

        frameOrder = order
        totalFrameCount = total
    }

    private fun updateSliderGripPosition() {
        val curRect = Rect()
        getLocalVisibleRect(curRect)
//        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Frame WxH = ${curRect.width()} x ${curRect.height()}")

        val tabRange = curRect.height() - SLIDER_GRIP_HEIGHT_PIX //
        val topMargin = min(tabRange / totalFrameCount, SLIDER_GRIP_HEIGHT_PIX) * frameOrder
//        if (Log.IS_DEBUG) {
//            Log.logDebug(TAG, "## tabRange = $tabRange")
//            Log.logDebug(TAG, "## topMargin = $topMargin")
//        }

        // Grip position.
        val layoutParams = slider_grip.layoutParams as FrameLayout.LayoutParams
        layoutParams.topMargin = topMargin
        slider_grip.layoutParams = layoutParams
    }

    private inner class LayoutObserverImpl : ViewTreeObserver.OnGlobalLayoutListener {
        private val TAG = "LayoutObserverImpl"

        override fun onGlobalLayout() {
//            if (Log.IS_DEBUG) Log.logDebug(TAG, "onGlobalLayout()")

            updateSliderGripPosition()
        }
    }

    fun isActive(): Boolean { return web_view.isActive }
    fun onResume() { web_view.onResume() }
    fun onPause() { web_view.onPause() }
    fun canGoBack(): Boolean { return web_view.canGoBack() }
    fun goBack() { web_view.goBack() }

    fun showGrip() {
        slider_grip.visibility = FrameLayout.VISIBLE
    }

    fun hideGrip() {
        slider_grip.visibility = FrameLayout.INVISIBLE
    }

    companion object {
        private const val TAG = "WebFrame"

        @SuppressLint("InflateParams")
        fun inflate(context: Context): WebFrame {
            return LayoutInflater.from(context).inflate(
                    R.layout.web_frame,
                    null) as WebFrame
        }
    }
}
