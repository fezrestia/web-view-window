@file:Suppress("PrivatePropertyName", "SameParameterValue", "ConstantConditionIf")

package com.fezrestia.android.webviewwindow.view

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.App

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class ExtendedWebView(context: Context) : WebView(context) {
    private lateinit var backHandlerThread: HandlerThread
    private lateinit var backHandler: Handler

    private lateinit var nopTask: EvalJsTask
    private lateinit var reloadTask: ReloadTask

    private val JSNI = JavaScriptNativeInterface()

    private val webViewClient = WebViewClientImpl()
    private val webChromeClient = WebChromeClientImpl()

    private inner class EvalJsCallback : ValueCallback<String> {
        private val TAG = "EvalJsCallback"

        override fun onReceiveValue(value: String) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceiveValue() : $value")
            // NOP.
        }
    }

    /**
     * Initialize.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        backHandlerThread = HandlerThread("back-worker", Thread.NORM_PRIORITY)
        backHandlerThread.start()
        backHandler = Handler(backHandlerThread.looper)

        // Web callback.
        setWebViewClient(webViewClient)
        setWebChromeClient(webChromeClient)

        // Debug.
        setWebContentsDebuggingEnabled(true)

        // Web setting.
        val webSettings = settings
        //        webSettings.setAllowContentAccess(true);
        //        webSettings.setAllowFileAccess(true);
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.setAppCacheEnabled(true)
        //        webSettings.setBlockNetworkImage(false);
        //        webSettings.setBlockNetworkLoads(false);
        //        webSettings.setBuiltInZoomControls(false);
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.databaseEnabled = true
        webSettings.displayZoomControls = false
        webSettings.domStorageEnabled = true
        webSettings.setGeolocationEnabled(true)
        webSettings.javaScriptEnabled = true
        //        webSettings.setLoadsImagesAutomatically(true);
        //        webSettings.setUseWideViewPort(true);
        //        webSettings.setUserAgentString("Desktop");

        addJavascriptInterface(JSNI, INJECTED_JAVA_SCRIPT_NATIVE_INTERFACE_OBJECT_NAME)

        // Java Script.
        val script = loadJs(JS_NOP)
        nopTask = EvalJsTask(script, EvalJsCallback())

        // Tasks.
        reloadTask = ReloadTask()
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

    /**
     * Release all references.
     */
    fun release() {
        stopLoading()
        clearCache(true)
        destroy()

        setWebViewClient(null)
        setWebChromeClient(null)

        App.ui.removeCallbacks(nopTask)
        App.ui.removeCallbacks(reloadTask)

        backHandlerThread.quitSafely()
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
        override fun onPageFinished(view: WebView, url: String) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPageFinished()")
            // NOP.
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "shouldOverrideUrlLoading() : E")
                Log.logDebug(TAG, "URL=$url")
            }

            this@ExtendedWebView.loadUrl(url)

            if (Log.IS_DEBUG) Log.logDebug(TAG, "shouldOverrideUrlLoading() : X")
            return true
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
                }
            }
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
