@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.webviewwindow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.R
import com.fezrestia.android.webviewwindow.receiver.WebViewWindowReceiver

class EnableDisableToggler : AppWidgetProvider() {

    override fun onUpdate(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onUpdate()")

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onEnabled()")

        // NOP.

    }

    override fun onDisabled(context: Context) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDisabled()")

        // NOP.

    }

    private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "updateAppWidget()")
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## widget ID = $appWidgetId")

        val endisToggleIntent = Intent(context.applicationContext, WebViewWindowReceiver::class.java)
        endisToggleIntent.action = Constants.INTENT_ACTION_TOGGLE_ENABLE_DISABLE
        val pendIntent = PendingIntent.getBroadcast(
                context,
                0, // private request code. dummy.
                endisToggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val views = RemoteViews(context.packageName, R.layout.widget_enable_disable_toggler)
        views.setOnClickPendingIntent(R.id.enable_disable_toggler_icon, pendIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        private const val TAG = "EnableDisableToggler"
    }
}
