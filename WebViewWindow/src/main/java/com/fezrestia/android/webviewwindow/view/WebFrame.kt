@file:Suppress("PrivatePropertyName")

package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.fezrestia.android.webviewwindow.R
import kotlinx.android.synthetic.main.web_frame.view.*
import kotlin.math.abs

class WebFrame(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int) : LinearLayout(context, attrs, defStyle) {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val TOUCH_SLOP: Int = ViewConfiguration.get(context).scaledTouchSlop

    private var callback: Callback? = null

    /**
     * WebFrame related event callback interface.
     */
    interface Callback {
        fun onTabClicked()

        fun onSlideWindowStarted(startedRawPos: Point)
        fun onSlideWindowOnGoing(startedRawPos: Point, diffPos: Point)
        fun onSlideWindowStopped(startedRawPos: Point, diffPos: Point, stoppedRawPos: Point)
    }

    /**
     * Initialize WebFrame.
     *
     * @param callback
     * @param baseUrl
     */
    fun initialize(
            callback: Callback,
            baseUrl: String) {
        this.callback = callback

        // Web view.
        web_view.initialize()
        web_view.onResume()
        web_view.loadUrl(baseUrl)

        // Slider grip.
        slider_grip.setOnTouchListener(SliderGripTouchEventListenerImpl())
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
                            callback?.onTabClicked()
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
        @SuppressLint("InflateParams")
        fun inflate(context: Context): WebFrame {
            return LayoutInflater.from(context).inflate(
                    R.layout.web_frame,
                    null) as WebFrame
        }
    }
}
