package com.fezrestia.android.webviewwindow

object Constants {
    /** Intent action to start overlay window. */
    const val INTENT_ACTION_START_OVERLAY_WINDOW = "com.fezrestia.android.webviewwindow.intent.ACTION_START_OVERLAY_WINDOW"
    /** Intent action to stop overlay window. */
    const val INTENT_ACTION_STOP_OVERLAY_WINDOW = "com.fezrestia.android.webviewwindow.intent.ACTION_STOP_OVERLAY_WINDOW"
    /** Intent action for overlay visibility switch. */
    const val INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY = "com.fezrestia.android.webviewwindow.intent.ACTION_TOGGLE_OVERLAY_VISIBILITY"
    /** Intent action to open overlay window. */
    const val INTENT_ACTION_OPEN_OVERLAY_WINDOW = "com.fezrestia.android.webviewwindow.intent.ACTION_OPEN_OVERLAY_WINDOW"

    /** SP Key, overlay en/disable.  */
    const val SP_KEY_WWW_ENABLE_DISABLE = "sp-key-www-enable-disable"
    /** SP Key, base load URL.  */
    const val SP_KEY_BASE_LOAD_URL = "sp-key-base-load-url"
    /** SP Key, WebView states on overlay window closed. */
    const val SP_KEY_LAST_WEB_VIEW_STATES_JSON = "sp-key-last-web-view-state-json"

    /** Default load URL.  */
    const val DEFAULT_BASE_LOAD_URL = "https://www.google.com"

    /** WakeLock name. */
    const val WAKE_LOCK_NAME = "WebViewWindow:WakeLock"

    /** WebView state key. */
    const val WEB_VIEW_STATE_BUNDLE_KEY = "WEBVIEW_CHROMIUM_STATE"
}
