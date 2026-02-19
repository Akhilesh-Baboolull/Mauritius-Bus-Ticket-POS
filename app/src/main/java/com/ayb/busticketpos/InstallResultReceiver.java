package com.ayb.busticketpos;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.Executors;

public class InstallResultReceiver extends BroadcastReceiver {
    private static final String TAG = "InstallResultReceiver";
    /** Delay before relaunching so the package is unfrozen after install (avoids SecurityException). */
    private static final long RELAUNCH_DELAY_MS = 3000L;
    /** Fallback: alarm in 5s to open app in case this receiver runs in a process that dies before the Handler runs. */
    private static final long RELAUNCH_ALARM_DELAY_MS = 5000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        int releaseId = intent.getIntExtra("releaseId", -1);
        int fromV = intent.getIntExtra("fromVersionCode", -1);
        int toV = intent.getIntExtra("toVersionCode", -1);

        try {
            JSONObject r = new JSONObject();
            r.put("releaseId", releaseId);
            r.put("fromVersionCode", fromV);
            r.put("toVersionCode", toV);
            r.put("stage", "INSTALL");

            if (status == PackageInstaller.STATUS_SUCCESS) {
                r.put("event", "INSTALL_SUCCESS");
                Log.i(TAG, "Install success");
                enqueueOnBackground(context, r, msg);
                Executors.newSingleThreadExecutor().execute(() ->
                        CrashLogger.logUpdateEvent(TAG, "INSTALL_SUCCESS"));

                Context appContext = context.getApplicationContext();
                Prefs.setPendingRelaunchAfterUnlock(appContext, true);
                // 1) Delayed relaunch in-process (package may be frozen if we start immediately)
                new Handler(Looper.getMainLooper()).postDelayed(() -> relaunchApp(appContext), RELAUNCH_DELAY_MS);
                // 2) Fallback: one-shot alarm to open app in 5s (in case this process is killed before the Handler runs)
                scheduleRelaunchAlarm(appContext);
            } else {
                // status=1 (STATUS_FAILURE) with empty message can occur on first attempt; retry may succeed (e.g. BTM04).
                r.put("event", "INSTALL_FAILED");
                r.put("errorCode", status);
                if (msg != null) r.put("message", msg);
                Log.e(TAG, "Install failed status=" + status + " msg=" + msg);
                final String crashMsg = "INSTALL_FAILED status=" + status + " message=" + (msg != null ? msg : "");
                enqueueOnBackground(context, r, msg);
                Executors.newSingleThreadExecutor().execute(() ->
                        CrashLogger.logUpdateEvent(TAG, crashMsg));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to report install result", e);
            CrashLogger.logUpdateEvent(TAG, "Failed to report install result", e);
        }
    }

    /** Room DB insert must not run on main thread (BroadcastReceiver). */
    private static void enqueueOnBackground(Context context, JSONObject r, String msg) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SyncQueueUtil.enqueue(context, UpdateConfig.REPORT_URL, r, msg);
            } catch (Exception e) {
                Log.e(TAG, "Failed to report install result", e);
                CrashLogger.logUpdateEvent(TAG, "Failed to report install result", e);
            }
        });
    }

    /**
     * Schedule a one-shot alarm to launch Menu in a few seconds. Works even if this process is killed
     * before the Handler runs (e.g. "Package is currently frozen" when system tries to start us).
     */
    private void scheduleRelaunchAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent launch = buildRelaunchIntent(context);
            PendingIntent pi = PendingIntent.getActivity(
                    context, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long triggerAt = System.currentTimeMillis() + RELAUNCH_ALARM_DELAY_MS;

            // Satisfy lint: check canScheduleExactAlarms (API 31+) or handle SecurityException
            boolean scheduledExact = false;
            if (am.canScheduleExactAlarms()) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                    scheduledExact = true;
                } catch (SecurityException e) {
                    Log.w(TAG, "Exact relaunch alarm not allowed, using inexact", e);
                }
            }
            if (!scheduledExact) {
                try {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } catch (SecurityException e) {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
            }
            Log.i(TAG, "Relaunch alarm set for " + (RELAUNCH_ALARM_DELAY_MS / 1000) + "s");
        } catch (Exception e) {
            Log.e(TAG, "Failed to set relaunch alarm", e);
            CrashLogger.logUpdateEvent(TAG, "Failed to set relaunch alarm: " + e.getMessage(), e);
        }
    }

    /**
     * Build intent to launch Menu so the app comes to front after update. Uses flags to show over
     * lock screen and turn screen on (kiosk: user should see the app, not Settings/lock).
     * Public so UnlockReceiver can use it on USER_PRESENT to bring app to front after unlock.
     */
    public static Intent buildRelaunchIntent(Context context) {
        Intent launch = new Intent(context, Menu.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        // Show-over-lock and turn-screen-on are set in Menu.onCreate() (setShowWhenLocked/setTurnScreenOn)
        return launch;
    }

    /**
     * Start the main (launcher) activity so the app reopens after a successful update.
     * Logs to updatelog-*.txt if startActivity fails (e.g. kiosk restrictions).
     */
    private void relaunchApp(Context context) {
        try {
            context.startActivity(buildRelaunchIntent(context));
            Log.i(TAG, "App relaunch intent sent");
        } catch (Exception e) {
            Log.e(TAG, "Failed to relaunch app after update", e);
            CrashLogger.logUpdateEvent(TAG, "INSTALL_SUCCESS but failed to relaunch app: " + e.getMessage(), e);
        }
    }
}
