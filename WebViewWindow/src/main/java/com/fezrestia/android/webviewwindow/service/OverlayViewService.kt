@file:Suppress("ConstantConditionIf", "MayBeConstant")

package com.fezrestia.android.webviewwindow.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder

import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.R
import com.fezrestia.android.webviewwindow.control.OverlayViewController
import com.fezrestia.android.util.Log

class OverlayViewService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onBind() : E")
        // NOP.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onBind() : X")
        return null
    }

    override fun onCreate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : E")
        super.onCreate()
        // NOP.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : X")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : E")

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
        val notification = Notification.Builder(this, ONGOING_NOTIFICATION_CHANNEL)
                .setContentTitle(getText(R.string.ongoing_notification))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(notificationContent)
                .build()

        startForeground(
                ONGOING_NOTIFICATION_ID,
                notification)

        OverlayViewController.singleton.start(this@OverlayViewService)

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : X")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : E")
        super.onDestroy()

        OverlayViewController.singleton.stop()

        stopForeground(true)

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : X")
    }

    companion object {
        private val TAG = "OverlayViewService"

        private val ONGOING_NOTIFICATION_CHANNEL = "ongoing"
        private val ONGOING_NOTIFICATION_ID = 100
    }
}
