package com.fezrestia.android.localcheckpointscheduler.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.fezrestia.android.localcheckpointscheduler.Constants;
import com.fezrestia.android.localcheckpointscheduler.R;
import com.fezrestia.android.localcheckpointscheduler.activity.UserPreferenceActivity;
import com.fezrestia.android.localcheckpointscheduler.control.OverlayViewController;
import com.fezrestia.android.localcheckpointscheduler.util.Log;

public class OverlayViewService extends Service {
    // Log tag.
    private static final String TAG = OverlayViewService.class.getSimpleName();

    // On going notification ID.
    private static final int ONGOING_NOTIFICATION_ID = 100;

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onBind() : E");
        // NOP.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onBind() : X");
        return null;
    }

    @Override
    public void onCreate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : E");
        super.onCreate();
        // NOP.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : X");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : E");

        // Preference trigger intent.
        Intent showOverlayTrigger = new Intent(Constants.INTENT_ACTION_OVERLAY_VIEW_TRIGGER);
        showOverlayTrigger.setPackage(getPackageName());
        PendingIntent notificationContent = PendingIntent.getBroadcast(
                this,
                0,
                showOverlayTrigger,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Foreground notification.
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getText(R.string.ongoing_notification))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(notificationContent)
                .build();

        // On foreground.
        startForeground(
                ONGOING_NOTIFICATION_ID,
                notification);

        // Load property.
        boolean isAlwaysReloadEnabled = intent.getExtras().getBoolean(
                Constants.SP_KEY_ALWAYS_RELOAD_ENABLED,
                false);

        // Start overlay view finder.
        OverlayViewController.getInstance().start(
                OverlayViewService.this,
                isAlwaysReloadEnabled);
        OverlayViewController.getInstance().resume();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : X");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : E");
        super.onDestroy();

        // Stop overlay view finder.
        OverlayViewController.getInstance().pause();
        OverlayViewController.getInstance().stop();

        // Stop foreground.
        stopForeground(true);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : X");
    }
}
