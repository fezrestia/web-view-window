@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.os.Message
import android.util.AttributeSet
import android.view.*
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
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

    private val DRAWABLE_SLIDER_GRIP_SELECTED = resources.getDrawable(R.drawable.slider_grip_selected, null)
    private val DRAWABLE_SLIDER_GRIP = resources.getDrawable(R.drawable.slider_grip, null)

    private var callback: Callback? = null
    private lateinit var webFrameContainerView: View
    private lateinit var rightBottomIconsContainerView: View

    private var frameOrder: Int = 0
    private var totalFrameCount: Int = 0
    private var isTopFrame: Boolean = true

    /**
     * WebFrame related event callback interface.
     */
    interface Callback {
        fun onTabClicked(frameOrder: Int)

        fun onSlideWindowStarted(startedRawPos: Point)
        fun onSlideWindowOnGoing(startedRawPos: Point, diffPos: Point)
        fun onSlideWindowStopped(startedRawPos: Point, diffPos: Point, stoppedRawPos: Point)

        fun onOpenNewWindowRequested(msg: Message)

        fun onCloseRequired(frameOrder: Int)
        fun onStartChromeCustomTabRequired(url: String)
    }

    private inner class ExtendedWebViewCallbackImpl : ExtendedWebView.Callback {
        override fun onNewWindowRequested(msg: Message) {
            callback?.onOpenNewWindowRequested(msg)
        }

        override fun onFaviconUpdated(favicon: Bitmap) {
            favicon_view.setImageBitmap(favicon)
        }

        override fun onNewUrlLoading(url: String) {
            val visible = if (url.startsWith("http://")) View.VISIBLE else View.INVISIBLE
            http_indicator.visibility = visible
        }
    }

    /**
     * Initialize WebFrame.
     *
     * @param callback
     * @param webFrameContainerView
     * @param rightBottomIconsContainerView
     */
    fun initialize(
            callback: Callback,
            webFrameContainerView: View,
            rightBottomIconsContainerView:View) {
        this.callback = callback
        this.webFrameContainerView = webFrameContainerView
        this.rightBottomIconsContainerView = rightBottomIconsContainerView

        // Web view.
        web_view.initialize(ExtendedWebViewCallbackImpl())
        web_view.onResume()

        // Slider grip.
        slider_grip.setOnTouchListener(SliderGripOnTouchListenerImpl())
        slider_grip.setOnLongClickListener(SliderGripOnLongClickListenerImpl())

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
        slider_grip.setOnLongClickListener(null)
        web_view.release()
    }

    private inner class SliderGripOnTouchListenerImpl : OnTouchListener {
        private val TAG = "SliderGripOnTouchListenerImpl"

        private var onDownRawX = 0
        private var onDownRawY = 0
        private var isDragging = false

        private fun diffX(curX: Int): Int {
            return curX - onDownRawX
        }

        private fun diffY(curY: Int): Int {
            return curY - onDownRawY
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val curX = event.rawX.toInt()
            val curY = event.rawY.toInt()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## ACTION_DOWN")

                    onDownRawX = curX
                    onDownRawY = curY
                }

                MotionEvent.ACTION_MOVE -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## ACTION_MOVE")

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
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## ACTION_UP/CANCEL")

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

            return false // to detect long-click.
        }
    }

    private inner class SliderGripOnLongClickListenerImpl : OnLongClickListener {
        private val TAG = "SliderGripOnLongClickListenerImpl"

        @SuppressLint("RtlHardcoded")
        override fun onLongClick(v: View?): Boolean {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## LONG-CLICK")

            if (!isTopFrame) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## NOP. isTopFrame == false")
                return false
            }

            val popup = PopupMenu(context, slider_grip)
            popup.menuInflater.inflate(R.menu.slider_grip_long_click_popup, popup.menu)
            popup.gravity = Gravity.RIGHT
            popup.setOnMenuItemClickListener(OnMenuItemClickListenerImpl())
            popup.setOnDismissListener(OnDismissListenerImpl())

            // Remove domain specific menu.
            if (getYoutubeVideoId(getCurrentUrl()) == null) {
                // This URL can NOT be converted to embed youtube URL.
                popup.menu.removeItem(R.id.popup_menu_youtube_endless_loop)
            }

            popup.show()

            return true
        }

        private inner class OnMenuItemClickListenerImpl : PopupMenu.OnMenuItemClickListener {
            private val TAG = "OnMenuItemClickListenerImpl"

            override fun onMenuItemClick(item: MenuItem): Boolean {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onMenuItemClick()")

                when (item.itemId) {
                    R.id.popup_menu_reload -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Popup : Reload")
                        web_view.reload()
                    }
                    R.id.popup_menu_close -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Popup : Close")
                        callback?.onCloseRequired(frameOrder)
                    }
                    R.id.popup_menu_open_on_chrome_custom_tab -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Popup : Open on Chrome Custom Tab")
                        callback?.onStartChromeCustomTabRequired(web_view.url)
                    }
                    R.id.popup_menu_youtube_endless_loop -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Popup : Youtube:EndlessLoop")
                        val videoId = getYoutubeVideoId(getCurrentUrl())
                        if (videoId != null) {
                            loadYoutubeEndlessLoop(videoId)
                        }
                    }
                }

                return true
            }
        }

        private inner class OnDismissListenerImpl : PopupMenu.OnDismissListener {
            private val TAG = "OnDismissListenerImpl"

            override fun onDismiss(menu: PopupMenu) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onDismiss()")
                menu.setOnMenuItemClickListener(null)
                menu.setOnDismissListener(null)
            }
        }

        private fun getYoutubeVideoId(url: String): String? {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## URL = $url")

            val videoIdRegex = Regex(
                    """\.youtube\.com/watch\?v=(.+)""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val videoIdResult = videoIdRegex.find(url)

            val videoId = if (videoIdResult != null) {
                videoIdResult.groupValues[1]
            } else {
                Log.logError(TAG, "## Failed to identify Youtube video ID.")
                return null
            }
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## Video ID = $videoId")

            return videoId
        }

        private fun loadYoutubeEndlessLoop(videoId: String) {
            val endlessLoopUrl = "https://www.youtube.com/embed/${videoId}?loop=1&playlist=${videoId}&autoplay=1"
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## Endless Loop URL = $endlessLoopUrl")
            loadUrl(endlessLoopUrl)
        }
    }

    /**
     * Order of this WebFrame.
     *
     * @param frameOrder 0 means top WebFrame.
     * @param totalFrameCount Count of WebFrames.
     * @param isTopFrame
     */
    fun setFrameOrder(frameOrder: Int, totalFrameCount: Int, isTopFrame: Boolean) {
        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "setFrameOrder()")
            Log.logDebug(TAG, "## frameOrder = $frameOrder")
            Log.logDebug(TAG, "## totalFrameCount = $totalFrameCount")
            Log.logDebug(TAG, "## isTopFrame = $isTopFrame")
        }

        this.frameOrder = frameOrder
        this.totalFrameCount = totalFrameCount
        this.isTopFrame = isTopFrame

        requestLayout()
    }

    private fun updateSliderGripPosition() {
        val topMargin: Int
        if (totalFrameCount == 1) {
            // Only 1 frame.
            topMargin = 0

            if (Log.IS_DEBUG) Log.logDebug(TAG, "## order=$frameOrder, Only 1 frame")
        } else {
            val containerHeight = webFrameContainerView.height
            val bottomIconsHeight = rightBottomIconsContainerView.height
            val tabRange = containerHeight - SLIDER_GRIP_HEIGHT_PIX - bottomIconsHeight
            val tabStep = min(tabRange / (totalFrameCount - 1), SLIDER_GRIP_HEIGHT_PIX)

            topMargin = tabStep * frameOrder

            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "## order=$frameOrder, containerHeight=$containerHeight")
                Log.logDebug(TAG, "## order=$frameOrder, bottomIconsHeight=$bottomIconsHeight")
                Log.logDebug(TAG, "## order=$frameOrder, tabRange = $tabRange")
                Log.logDebug(TAG, "## order=$frameOrder, tabStep = $tabStep")
                Log.logDebug(TAG, "## order=$frameOrder, topMargin = $topMargin")
            }
        }

        // Grip position.
        val layoutParams = slider_grip_container.layoutParams as LayoutParams
        if (layoutParams.topMargin != topMargin) {
            layoutParams.topMargin = topMargin
            slider_grip_container.layoutParams = layoutParams
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## order=$frameOrder, LayoutParams updated.")
        }

        // Grip icon.
        val nextDrawable = if (isTopFrame) {
            DRAWABLE_SLIDER_GRIP_SELECTED
        } else {
            DRAWABLE_SLIDER_GRIP
        }
        if (slider_grip.drawable != nextDrawable) {
            slider_grip.setImageDrawable(nextDrawable)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## order=$frameOrder, Drawable updated..")
        }
    }

    private inner class LayoutObserverImpl : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            updateSliderGripPosition()
        }
    }

    fun isActive(): Boolean { return web_view.isActive }
    fun onResume() { web_view.onResume() }
    fun onPause() { web_view.onPause() }
    fun canGoBack(): Boolean { return web_view.canGoBack() }
    fun goBack() { web_view.goBack() }
    fun getCurrentUrl(): String { return web_view.url }

    fun showGrip() {
        slider_grip_container.visibility = FrameLayout.VISIBLE
    }

    fun hideGrip() {
        slider_grip_container.visibility = FrameLayout.INVISIBLE
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
