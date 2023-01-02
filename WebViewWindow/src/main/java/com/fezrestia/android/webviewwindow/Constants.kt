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
    /** Intent action to enable/disable window. */
    const val INTENT_ACTION_TOGGLE_ENABLE_DISABLE = "com.fezrestia.android.webviewwindow.intent.ACTION_TOGGLE_ENABLE_DISABLE"

    /** SP Key, overlay en/disable.  */
    const val SP_KEY_WWW_ENABLE_DISABLE = "sp-key-www-enable-disable"
    /** SP Key, base load URL.  */
    const val SP_KEY_BASE_LOAD_URL = "sp-key-base-load-url"
    /** SP Key, WebView states on overlay window closed. */
    const val SP_KEY_LAST_WEB_VIEW_STATES_JSON = "sp-key-last-web-view-state-json"
    /** SP Key, custom user-agent string. */
    const val SP_KEY_CUSTOM_USER_AGENT = "sp-key-custom-user-agent"

    /** SP key, DEBUG, force crash. */
    const val SP_KEY_DEBUG_FORCE_CRASH = "sp-key-debug-force-crash"

    /** Default load URL.  */
    const val DEFAULT_BASE_LOAD_URL = "https://www.google.com"

    /** WakeLock name. */
    const val WAKE_LOCK_NAME = "WebViewWindow:WakeLock"

    /** WebView state key. */
    const val WEB_VIEW_STATE_BUNDLE_KEY = "WEBVIEW_CHROMIUM_STATE"

    /** Firebase analytics event. */
    object FirebaseEvent {
        object LowMemState {
            const val EVENT = "Low_Mem_State"

            object Params {
                object Key {
                    const val ON_TRIM_MEMORY = "ON_TRIM_MEMORY"
                }

                object Value {
                    const val RUNNING_MODERATE  = "RUNNING_MODERATE"
                    const val RUNNING_LOW       = "RUNNING_LOW"
                    const val RUNNING_CRITICAL  = "RUNNING_CRITICAL"
                    const val UI_HIDDEN         = "UI_HIDDEN"
                    const val BACKGROUND        = "BACKGROUND"
                    const val MODERATE          = "MODERATE"
                    const val COMPLETE          = "COMPLETE"
                    const val ELSE              = "ELSE"
                }
            }
        }

        object RenderProcessState {
            const val EVENT = "Render_Process_State"

            object Params {
                object Key {
                    const val ON_RENDER_PROCESS_GONE = "ON_RENDER_PROCESS_GONE"
                }

                object Value {
                    const val CRASH                     = "CRASH"
                    const val LMK_ON_PRIORITY_WAIVED    = "LMK_ON_PRIORITY_WAIVED"
                    const val LMK_ON_PRIORITY_BOUND     = "LMK_ON_PRIORITY_BOUND"
                    const val LMK_ON_PRIORITY_IMPORTANT = "LMK_ON_PRIORITY_IMPORTANT"
                    const val ELSE                      = "ELSE"
                }
            }
        }
    }
}
