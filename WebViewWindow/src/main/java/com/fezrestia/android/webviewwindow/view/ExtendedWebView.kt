@file:Suppress("PrivatePropertyName", "SameParameterValue", "ConstantConditionIf")

package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.AttributeSet
import android.webkit.*

import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.App

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class ExtendedWebView(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int) : WebView(context, attrs, defStyle) {
    private lateinit var backHandlerThread: HandlerThread
    private lateinit var backHandler: Handler

    private lateinit var nopTask: EvalJsTask
    private lateinit var reloadTask: ReloadTask

    private val JSNI = JavaScriptNativeInterface()

    private val webViewClient = WebViewClientImpl()
    private val webChromeClient = WebChromeClientImpl()

    /** Extended WebView is active or not. */
    var isActive = false
        private set

    private var callback: Callback? = null

    /**
     * Extended WebView callback interface.
     */
    interface Callback {
        /**
         * Request open URL on new WebFrame.
         *
         * @param msg Message for WebViewTransport to open new URL.
         */
        fun onNewWindowRequested(msg: Message)

        /**
         * Called every on favicon image is loaded for current page.
         *
         * @param favicon
         */
        fun onFaviconUpdated(favicon: Bitmap)

        /**
         * Called every on URL is changed.
         *
         * @param url
         */
        fun onNewUrlLoading(url: String)
    }

    private inner class EvalJsCallback : ValueCallback<String> {
        private val TAG = "EvalJsCallback"

        override fun onReceiveValue(value: String) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceiveValue() : $value")
            // NOP.
        }
    }

    // CONSTRUCTOR.
    constructor(context: Context) : this(context, null) {
        // NOP.
    }

    // CONSTRUCTOR.
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {
        // NOP.
    }

    /**
     * Initialize.
     *
     * @param callback
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(callback: Callback) {
        this.callback = callback

        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "## WebView version = ${getCurrentWebViewPackage()}")
        }

        backHandlerThread = HandlerThread("back-worker", Thread.NORM_PRIORITY)
        backHandlerThread.start()
        backHandler = Handler(backHandlerThread.looper)

        // Set rendering process priority to be targeted by LMK if not visible.
        // TODO: Enable this with onRenderProcessGone() implementation.
//        setRendererPriorityPolicy(RENDERER_PRIORITY_BOUND, true)

        // Web callback.
        setWebViewClient(webViewClient)
        setWebChromeClient(webChromeClient)

        // Debug.
//        setWebContentsDebuggingEnabled(true)

        // Web setting.
        val webSettings = settings
        // Disable HTTP access.
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        // Enable tab browsing.
        webSettings.setSupportMultipleWindows(true)
        // Access permission.
        webSettings.allowContentAccess = true
        webSettings.allowFileAccess = true
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        // Enable cache.
        webSettings.setAppCacheEnabled(true)
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.setAppCachePath(context.cacheDir.absolutePath)
        // Enable resource load.
        webSettings.blockNetworkImage = false
        webSettings.blockNetworkLoads = false
        webSettings.loadsImagesAutomatically = true
        // Enable zoom.
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        // Enable database API.
        webSettings.databaseEnabled = true
        // Enable DOM API.
        webSettings.domStorageEnabled = true
        // Enable location API.
        webSettings.setGeolocationEnabled(true)
        // Enable Javascript.
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        // User Agent.
        val old = webSettings.userAgentString
        val pkgName = context.packageName
        val pkgInfo = context.packageManager.getPackageInfo(pkgName, PackageManager.GET_META_DATA)
        val verName = pkgInfo.versionName
        webSettings.userAgentString = "$old WebViewWindow/$verName"

        // Java Script Native Interface.
        addJavascriptInterface(JSNI, INJECTED_JAVA_SCRIPT_NATIVE_INTERFACE_OBJECT_NAME)

        // Java Script.
        val script = loadJs(JS_NOP)
        nopTask = EvalJsTask(script, EvalJsCallback())

        // Tasks.
        reloadTask = ReloadTask()
    }

    override fun onResume() {
        super.onResume()
        isActive = true
    }

    override fun onPause() {
        isActive = false
        super.onPause()
    }

    /**
     * Release all references.
     */
    fun release() {
        callback = null

        stopLoading()
        onPause()

        clearCache(true)
        clearHistory()

        setWebViewClient(null)
        setWebChromeClient(null)
        removeJavascriptInterface(INJECTED_JAVA_SCRIPT_NATIVE_INTERFACE_OBJECT_NAME)

        App.ui.removeCallbacks(nopTask)
        App.ui.removeCallbacks(reloadTask)

        backHandlerThread.quitSafely()

        destroy() // Final API calling for WebView.
    }

    private fun loadJs(assetsName: String): String {
        var script = ""

        try {
            val fis = context.assets.open(assetsName)
            val reader = BufferedReader(InputStreamReader(fis, "UTF-8"))
            var tmp: String?
            while (true) {
                tmp = reader.readLine()
                if (tmp == null) break

                script = script + tmp + '\n'.toString()
            }
            reader.close()
            fis.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return script
    }

    override fun reload() {
        stopLoading()
        super.reload()
    }

    private inner class ReloadTask : Runnable {
        override fun run() {
            reload()
        }
    }

    /**
     * Evaluate JavaScript on WebView.
     *
     * @constructor
     * @param script JavaScript source codes.
     * @param callback JavaScript done callback.
     */
    private inner class EvalJsTask(
            private val script: String,
            private val callback: ValueCallback<String>) : Runnable {
        override fun run() {
            evaluateJavascript(script, callback)
        }
    }

    private inner class JavaScriptNativeInterface {
        private val TAG = "JSNI"

        /**
         * Called on content HTML loaded.
         *
         * @param htmlSrc Loaded HTML source codes.
         */
        @Suppress("unused")
        @JavascriptInterface
        fun onContentHtmlLoaded(htmlSrc: String) {
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "onPageFinished()")
                Log.logDebug(TAG, "HTML = \n$htmlSrc")
            }
            // NOP.
        }
    }

    private inner class WebViewClientImpl : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPageStarted()")

            if (favicon != null) {
                callback?.onFaviconUpdated(favicon)
            }

            callback?.onNewUrlLoading(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPageFinished()")
            // NOP.
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "shouldOverrideUrlLoading() : E")
                Log.logDebug(TAG, "URL=${request.url}")
            }

            // NOP.

            if (Log.IS_DEBUG) Log.logDebug(TAG, "shouldOverrideUrlLoading() : X")
            return false
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "onReceivedError()")
                Log.logDebug(TAG, "  ERR Code = ${error.errorCode}")
                Log.logDebug(TAG, "  ERR Desc = ${error.description}")
            }

            if (request.isForMainFrame) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceivedError() : for Main frame")

                //TODO: Handle load error.

            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceivedError() : for other")
                // NOP.
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "onReceivedSslError()")
                Log.logDebug(TAG, "  URL = ${error.url}")
                Log.logDebug(TAG, "  primaryError = ${error.primaryError}")
            }

            //TODO: Handle SSL Error.

        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onRenderProcessGone()")

            if (!detail.didCrash()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## WebView Rendering Process is killed by LMK.")

                // TODO: Handle WebView close.

            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## WebView Rendering Process is crashed.")

                // TODO: Handle crash.

            }

            return true // Continue to run App.
        }
    }

    private inner class WebChromeClientImpl : WebChromeClient() {
        override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onGeolocationPermissionShowPrompt()")

            callback.invoke(origin, true, true) // Allow to use geo API and retain this.
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPermissionRequest()")

            if (Log.IS_DEBUG) {
                for (resource in request.resources) {
                    Log.logDebug(TAG, "    Permission=$resource")

                    //TODO: Handle permission request.

                }
            }
        }

        override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message): Boolean {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreateWindow()")

            if (!isUserGesture) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## isUserGesture == false")
                // Maybe pop-up window. Do not handle it.
                return false
            }
            if (isDialog) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## isDialog == true")
                // Dialog will be on same WebFrame.
                return false
            }

            // Request new tab.
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## Open new Window")
            callback?.onNewWindowRequested(resultMsg)

            return true
        }

        override fun onReceivedIcon(view: WebView, favicon: Bitmap) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceivedIcon()")

            callback?.onFaviconUpdated(favicon)
        }
    }

    companion object {
        private const val TAG = "ExtendedWebView"

        // Java Script -> Native Java interface.
        private const val INJECTED_JAVA_SCRIPT_NATIVE_INTERFACE_OBJECT_NAME = "jsni"

        // JS file.
        private const val JS_NOP = "nop.js"
    }
}
