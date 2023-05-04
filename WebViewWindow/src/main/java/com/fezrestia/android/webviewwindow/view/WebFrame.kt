@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.os.Bundle
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.*
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.BuildConfig
import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.R
import kotlinx.android.synthetic.main.web_frame.view.*
import org.apache.commons.validator.routines.UrlValidator
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

    private val DRAWABLE_SLIDER_GRIP_SELECTED = ResourcesCompat.getDrawable(
            resources,
            R.drawable.slider_grip_selected,
            null)
    private val DRAWABLE_SLIDER_GRIP = ResourcesCompat.getDrawable(
            resources,
            R.drawable.slider_grip,
            null)

    private var callback: Callback? = null
    private lateinit var webFrameContainerView: View
    private lateinit var rightBottomIconsContainerView: View

    private var frameOrder: Int = 0
    private var totalFrameCount: Int = 0
    private var isTopFrame: Boolean = true

    private val MAX_SCALE_RATE_PERCENT = 1000
    private val MIN_SCALE_RATE_PERCENT = 10
    private val SCALE_RATE_PERCENT_STEP = 10
    private var currentScaleRatePercent = 100

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

        fun onUrlChanged(url: String)

        fun onToggleFocusRequested()
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

            nav_bar_url.text = url

            callback?.onUrlChanged(url)
        }
    }

    /**
     * Initialize WebFrame.
     *
     * @param callback
     * @param webFrameContainerView
     * @param rightBottomIconsContainerView
     * @param defaultUrl
     */
    fun initialize(
            callback: Callback,
            webFrameContainerView: View,
            rightBottomIconsContainerView:View,
            defaultUrl: String?) {
        this.callback = callback
        this.webFrameContainerView = webFrameContainerView
        this.rightBottomIconsContainerView = rightBottomIconsContainerView

        // Web view.
        web_view.initialize(ExtendedWebViewCallbackImpl())
        web_view.onResume()

        // Navigation bar.
        nav_bar_back_button.setOnClickListener(NavBarBackButtonOnClickListener())
        nav_bar_fore_button.setOnClickListener(NavBarForeButtonOnClickListener())
        nav_bar_reload_button.setOnClickListener(NavBarReloadButtonOnClickListener())
        nav_bar_toggle_focus_button.setOnClickListener(NavBarToggleFocusButtonOnClickListener())
        nav_bar_scale_down_button.setOnClickListener(NavBarScaleDownButtonOnClickListener())
        nav_bar_scale_up_button.setOnClickListener(NavBarScaleUpButtonOnClickListener())
        nav_bar_scale_reset_button.setOnClickListener(NavBarScaleResetButtonOnClickListener())

        // Slider grip.
        slider_grip.setOnTouchListener(SliderGripOnTouchListenerImpl())
        slider_grip.setOnLongClickListener(SliderGripOnLongClickListenerImpl())

        // Per-layout process.
        viewTreeObserver.addOnGlobalLayoutListener(LayoutObserverImpl())

        setupEntryView(defaultUrl)
    }

    @SuppressLint("SetTextI18n")
    private fun setupEntryView(defaultUrl: String?) {
        val invalidUrlErrorMsg = resources.getString(R.string.invalid_url_error_msg)

        footer_label.text = "WebViewWindow:${BuildConfig.VERSION_NAME}"

        // If hit set to TextInputLayout, hint is always displayed above EditText.
        base_load_url_input.hint = defaultUrl

        base_load_url_input.addTextChangedListener( object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // NOP.
            }

            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                val url = text.toString()
                if (url.isEmpty() || isValidUrl(url)) {
                    base_load_url.isErrorEnabled = false
                    base_load_url.error = null
                } else {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## Invalid URL = $url")
                    base_load_url.isErrorEnabled = true
                    base_load_url.error = invalidUrlErrorMsg
                }
            }

            override fun afterTextChanged(s: Editable) {
                // NOP.
            }
        } )

        paste_button.setOnClickListener {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "paste_button.onClick()")

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipDesc = clipboard.primaryClipDescription

            val hasClip: Boolean = clipboard.hasPrimaryClip()
            val hasText: Boolean = clipDesc?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ?: false
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## hasClip = $hasClip, hasText = $hasText")

            if (hasClip || hasText) {
                val textItem = clipboard.primaryClip?.getItemAt(0)?.text
                if (textItem != null) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## textItem = $textItem")
                    base_load_url_input.setText(textItem, TextView.BufferType.EDITABLE)
                }
            }
        }

        load_url_button.setOnClickListener {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "load_url_button.onClick()")

            val url = base_load_url_input.text.toString()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## URL = $url")

            if (url.isEmpty()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Load default URL = $defaultUrl")

                if (defaultUrl != null) {
                    loadUrl(defaultUrl)
                }
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Start load.")

                if (isValidUrl(url)) {
                    loadUrl(url)
                } else {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## Invalid URL = $url")
                    base_load_url.error = invalidUrlErrorMsg
                }
            }
        }
    }

    private fun isValidUrl(url: String): Boolean {
        val schemes = arrayOf("http", "https")
        val urlValidator = UrlValidator(schemes)
        return urlValidator.isValid(url)
    }

    /**
     * Start loading URL.
     *
     * @param url
     */
    fun loadUrl(url: String) {
        web_view.loadUrl(url)
        entry_view.visibility = GONE
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
        entry_view.visibility = GONE
    }

    /**
     * Start loading with WebView state of Bundle.
     *
     * @param state
     */
    fun loadState(state: Bundle) {
        web_view.restoreState(state)
        entry_view.visibility = GONE
    }

    /**
     * Release ALL references.
     */
    fun release() {
        callback = null

        // Navigation bar.
        nav_bar_back_button.setOnClickListener(null)
        nav_bar_fore_button.setOnClickListener(null)
        nav_bar_reload_button.setOnClickListener(null)
        nav_bar_toggle_focus_button.setOnClickListener(null)
        nav_bar_scale_down_button.setOnClickListener(null)
        nav_bar_scale_up_button.setOnClickListener(null)

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

                            if (web_view.isReloadRequired) {
                                web_view.reload()
                            }
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
            getCurrentUrl()?.let {
                if (getYoutubeVideoId(it) == null) {
                    // This URL can NOT be converted to embed youtube URL.
                    popup.menu.removeItem(R.id.popup_menu_youtube_endless_loop)
                }
            }

            // Remove current User-Agent changer.
            when (web_view.currentUserAgentKey) {
                Constants.SP_KEY_CUSTOM_USER_AGENT_FOR_MOBILE -> {
                    popup.menu.removeItem(R.id.popup_menu_change_user_agent_to_mobile)
                }
                Constants.SP_KEY_CUSTOM_USER_AGENT_FOR_DESKTOP -> {
                    popup.menu.removeItem(R.id.popup_menu_change_user_agent_to_desktop)
                }
            }

            popup.show()

            return true
        }

        private inner class OnMenuItemClickListenerImpl : PopupMenu.OnMenuItemClickListener {
            private val TAG = "OnMenuItemClickListenerImpl"

            override fun onMenuItemClick(item: MenuItem): Boolean {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onMenuItemClick()")

                when (item.itemId) {
                    R.id.popup_menu_close -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Popup : Close")
                        callback?.onCloseRequired(frameOrder)
                    }
                    R.id.popup_menu_open_on_chrome_custom_tab -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Popup : Open on Chrome Custom Tab")
                        web_view.url?.let {
                            callback?.onStartChromeCustomTabRequired(it)
                        }
                    }
                    R.id.popup_menu_youtube_endless_loop -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Popup : Youtube:EndlessLoop")
                        getCurrentUrl()?.let {
                            val videoId = getYoutubeVideoId(it)
                            if (videoId != null) {
                                loadYoutubeEndlessLoop(videoId)
                            }
                        }
                    }
                    R.id.popup_menu_change_user_agent_to_mobile -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Popup : Change User-Agent to Mobile")
                        web_view.updateUserAgentBySharedPreferenceKey(Constants.SP_KEY_CUSTOM_USER_AGENT_FOR_MOBILE)
                        web_view.reload()
                    }
                    R.id.popup_menu_change_user_agent_to_desktop -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Popup : Change User-Agent to Desktop")
                        web_view.updateUserAgentBySharedPreferenceKey(Constants.SP_KEY_CUSTOM_USER_AGENT_FOR_DESKTOP)
                        web_view.reload()
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

    private inner class NavBarBackButtonOnClickListener : OnClickListener {
        override fun onClick(v: View?) {
            if (web_view.canGoBack()) {
                web_view.goBack()
            }
        }
    }

    private inner class NavBarForeButtonOnClickListener : OnClickListener {
        override fun onClick(v: View?) {
            if (web_view.canGoForward()) {
                web_view.goForward()
            }
        }
    }

    private inner class NavBarReloadButtonOnClickListener : OnClickListener {
        override fun onClick(v: View?) {
            web_view.reload()
        }
    }

    private inner class NavBarToggleFocusButtonOnClickListener : OnClickListener {
        override fun onClick(v: View?) {
            callback?.onToggleFocusRequested()
        }
    }

    private inner class NavBarScaleDownButtonOnClickListener : OnClickListener {
        override fun onClick(v: View?) {
            currentScaleRatePercent -= SCALE_RATE_PERCENT_STEP
            doChangeScaleRatePercent()
        }
    }

    private inner class NavBarScaleUpButtonOnClickListener : OnClickListener {
        override fun onClick(v: View?) {
            currentScaleRatePercent += SCALE_RATE_PERCENT_STEP
            doChangeScaleRatePercent()
        }
    }

    private inner class NavBarScaleResetButtonOnClickListener : OnClickListener {
        override fun onClick(v: View?) {
            currentScaleRatePercent = 100
            web_view.setInitialScale(0)
        }
    }

    private fun doChangeScaleRatePercent() {
        // Check limit.
        if (currentScaleRatePercent < MIN_SCALE_RATE_PERCENT) {
            currentScaleRatePercent = MIN_SCALE_RATE_PERCENT
        }
        if (currentScaleRatePercent > MAX_SCALE_RATE_PERCENT) {
            currentScaleRatePercent = MAX_SCALE_RATE_PERCENT
        }

        // Do change.
        web_view.setInitialScale(currentScaleRatePercent)
    }

    fun isActive(): Boolean { return web_view.isActive }
    fun onResume() { web_view.onResume() }
    fun onPause() { web_view.onPause() }
    fun canGoBack(): Boolean { return web_view.canGoBack() }
    fun goBack() { web_view.goBack() }
    fun getCurrentUrl(): String? { return web_view.url }

    /**
     * Get current WebView state as Bundle.
     *
     * @return Current WebView state.
     */
    fun getWebViewState(): Bundle {
        val state = Bundle()
        web_view.saveState(state)

        if (Log.IS_DEBUG) Log.logDebug(TAG, "bundle state = $state")

        return state
    }

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
