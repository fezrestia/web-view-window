@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.R
import com.fezrestia.android.webviewwindow.service.WebViewWindowService

class ChromeCustomTabBaseActivity : Activity() {

    // Start chrome custom tab only once.
    private var shouldBeFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate()")

        shouldBeFinished = false
    }

    override fun onResume() {
        super.onResume()
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")

        if (!shouldBeFinished) {
            // 1st time.

            val builder = CustomTabsIntent.Builder()
            builder.enableUrlBarHiding()
            builder.setStartAnimations(
                    this,
                    R.anim.slide_in_from_right,
                    R.anim.slide_out_to_left)
            builder.setExitAnimations(
                    this,
                    R.anim.slide_in_from_left,
                    R.anim.slide_out_to_right)
            val chromeIntent = builder.build()
            chromeIntent.intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            this.intent.data?.also {
                chromeIntent.launchUrl(this, it)
            } ?: run {
                Toast.makeText(this, "ERROR: URI is null", Toast.LENGTH_LONG).show()
            }

            shouldBeFinished = true

        } else {
            // May be returned from Chrome Custom Tab.

            // Request to open overlay window.
            val service = Intent(Constants.INTENT_ACTION_OPEN_OVERLAY_WINDOW)
            service.setClass(
                    this.applicationContext,
                    WebViewWindowService::class.java)
            this.startForegroundService(service)

            finish()
        }
    }

    override fun onPause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")
        // NOP.
        super.onPause()
    }

    override fun onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy()")
        // NOP.
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChromeCustomTabBaseActivity"
    }
}
