@file:Suppress("ConstantConditionIf", "PrivatePropertyName")

package com.fezrestia.android.webviewwindow.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.R
import com.fezrestia.android.webviewwindow.control.WebViewWindowController
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.App
import com.fezrestia.android.webviewwindow.view.WebViewWindowRootView

class WebViewWindowService : Service() {

    private lateinit var controller: WebViewWindowController
    private lateinit var view: WebViewWindowRootView

    override fun onBind(intent: Intent): IBinder? {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onBind() : E")
        // NOP.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onBind() : X")
        return null
    }

    private fun getForegroundServiceNotification(): Notification {
        val notifyIntent = Intent(Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY)
        notifyIntent.setPackage(packageName)

        val notificationContent = PendingIntent.getBroadcast(
                this,
                0,
                notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val channel = NotificationChannel(
                ONGOING_NOTIFICATION_CHANNEL,
                getText(R.string.ongoing_notification),
                NotificationManager.IMPORTANCE_MIN)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, ONGOING_NOTIFICATION_CHANNEL)
                .setContentTitle(getText(R.string.ongoing_notification))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(notificationContent)
                .build()
    }

    override fun onCreate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : E")
        super.onCreate()

        if (App.isEnabled) throw RuntimeException("Already Enabled.")

        startForeground(
                ONGOING_NOTIFICATION_ID,
                getForegroundServiceNotification())

        view = WebViewWindowRootView.inflate(this)
        view.initialize(ViewCallback())

        controller = WebViewWindowController(this)

        App.isEnabled = true

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : X")
    }

    override fun onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : E")
        super.onDestroy()

        if (!App.isEnabled) throw RuntimeException("Already Disabled.")

        view.release()

        controller.release()

        stopForeground(true)

        App.isEnabled = false

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : X")
    }

    private inner class ViewCallback : WebViewWindowRootView.Callback {
        private val TAG = "ViewCallback"

        override fun onNewWebFrameRequired() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onNewWebFrameRequired()")
            view.addNewWebFrameToTailWithUrl(controller.getDefaultUrl())
        }

        override fun onFullBrowserRequired(url: String) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onFullBrowserRequired() : url=$url")
            controller.startChromeCustomTab(url)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : E")

        if (intent.action == null) {
            Log.logError(TAG, "ACTION == NULL")
        } else {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION = ${intent.action}")
            when (intent.action) {
                Constants.INTENT_ACTION_START_OVERLAY_WINDOW -> {
                    controller.start()

                    view.addToOverlayWindow()

                    val urls = controller.loadUrls()
                    if (urls.isEmpty()) {
                        // Open 1 default WebFrame.
                        view.addNewWebFrameToTailWithUrl(controller.getDefaultUrl())
                    } else {
                        // Restore last URLs.
                        urls.forEach { url ->
                            view.addNewWebFrameToTailWithUrl(url)
                        }
                    }
                }

                Constants.INTENT_ACTION_STOP_OVERLAY_WINDOW -> {
                    val urls = view.getCurrentUrls()
                    controller.saveUrls(urls)

                    view.removeFromOverlayWindow()

                    controller.stop()

                    stopSelf()
                }

                Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY -> {
                    val isStateChanged = view.toggleShowHide()
                    if (isStateChanged) {
                        if (view.isHidden()) {
                            controller.stop()
                        } else {
                            controller.start()
                        }
                    }
                }

                Constants.INTENT_ACTION_OPEN_OVERLAY_WINDOW -> {
                    view.startSlideInWindow()
                }

                else -> throw RuntimeException("Unexpected ACTION = ${intent.action}")
            }
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : X")
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "WebViewWindowService"

        private const val ONGOING_NOTIFICATION_CHANNEL = "ongoing"
        private const val ONGOING_NOTIFICATION_ID = 100

    }
}
