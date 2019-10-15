package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.fezrestia.android.webviewwindow.R

import kotlinx.android.synthetic.main.web_frame.view.*

class WebFrame(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int) : LinearLayout(context, attrs, defStyle) {

    // CONSTRUCTOR.
    constructor(context: Context) : this(context, null) {
        // NOP.
    }

    // CONSTRUCTOR.
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {
        // NOP.
    }

    /**
     * Initialize WebFrame.
     *
     * @param sliderGripTouchEventHandler
     * @param baseUrl
     */
    fun initialize(
            sliderGripTouchEventHandler: OnTouchListener,
            baseUrl: String) {
        // Web view.
        web_view.initialize()
        web_view.onResume()
        web_view.loadUrl(baseUrl)

        // Slider grip.
        slider_grip_container.setOnTouchListener(sliderGripTouchEventHandler)
    }

    /**
     * Release ALL references.
     */
    fun release() {
        slider_grip_container.setOnTouchListener(null)
        web_view.release()
    }

    fun isActive(): Boolean { return web_view.isActive }
    fun onResume() { web_view.onResume() }
    fun onPause() { web_view.onPause() }
    fun canGoBack(): Boolean { return web_view.canGoBack() }
    fun goBack() { web_view.goBack() }

    fun showGrip() {
        slider_grip_container.visibility = FrameLayout.VISIBLE
    }

    fun hideGrip() {
        slider_grip_container.visibility = FrameLayout.INVISIBLE
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
