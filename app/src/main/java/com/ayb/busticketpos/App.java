package com.ayb.busticketpos;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.work.WorkManager;

import java.util.Calendar;

public class App extends Application {
    /** Daily alarm time for DB fetch + update check (change here for tests, e.g. 18 and 30 for 18:30). */
    private static final int ALARM_HOUR = 21;
    private static final int ALARM_MINUTE = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        CrashLogger.init(this);

        // When running in :bg process (location sync), do minimal work so the process is ready
        // quickly and LocationForegroundService can call startForeground() within the timeout.
        if (isBgProcess(this)) {
            Log.d("App", "onCreate: :bg process — minimal init + schedule daily fetch alarm");
            Prefs.setLastAlive(this, System.currentTimeMillis());
            // Still schedule the daily update alarm so it fires even if the main process never ran.
            scheduleDailyFetch(this);
            return;
        }

        Log.d("App", "onCreate: main process — full init");
        MemoryWatchdog.init(this);
        Prefs.setLastAlive(this, System.currentTimeMillis());

        // One-time: clear WorkManager job flood on devices where Clear data is greyed out (e.g. device owner).
        if (!Prefs.getWorkManagerFloodCleaned(this)) {
            try {
                WorkManager.getInstance(this).cancelAllWork();
                Prefs.setWorkManagerFloodCleaned(this, true);
                Log.i("App", "One-time WorkManager cancelAllWork done (cleared job flood)");
            } catch (Exception e) {
                Log.w("App", "WorkManager cancelAllWork failed", e);
            }
        }
        // Clean up old finished/cancelled WorkManager jobs; start location service.
        try {
            WorkManager.getInstance(this).pruneWork();
            LocationForegroundService.startIfNeeded(this);
        } catch (Exception e) {
            Log.w("App", "WorkManager prune failed", e);
        }

        scheduleDailyFetch(this);

        // After update relaunch: run DB sync once (update-first flow)
        if (Prefs.getAndClearRunDbSyncOnNextLaunch(this)) {
            try {
                AppDatabase.forceRefresh(this);
            } catch (Exception e) {
                android.util.Log.w("App", "DB sync on launch failed", e);
            }
        }
    }

    /** True if we are in the :bg process (location sync). When process name is unknown we treat as main so full init runs (alarm + WorkManager). */
    private static boolean isBgProcess(Context ctx) {
        String processName = getProcessNameSafe(ctx);
        return processName != null && processName.endsWith(":bg");
    }

    private static String getProcessNameSafe(Context ctx) {
        try {
            return Application.getProcessName();
        } catch (Exception e) {
            Log.w("App", "getProcessName failed", e);
        }
        return null;
    }

    /**
     * Schedule the next daily fetch/update alarm at ALARM_HOUR:ALARM_MINUTE (today or tomorrow).
     * Uses an exact alarm (setExactAndAllowWhileIdle / setExact) so it fires on time even in Doze;
     * setRepeating() was inexact and could delay by an hour or more. Call from Application.onCreate()
     * and from FetchReceiver after it runs so the next day is scheduled (exact alarms are one-shot).
     * Defensive: always attempts to set some alarm; on any failure falls back to inexact set().
     */
    public static void scheduleDailyFetch(Context context) {
        Context ctx = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w("SyncTriggered", "AlarmManager null, cannot schedule daily fetch");
            return;
        }

        // Explicit intent + package so the broadcast is delivered to our app (required on Android 12+ and with multiple processes).
        Intent intent = new Intent(ctx, FetchReceiver.class);
        intent.setPackage(ctx.getPackageName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set trigger time to ALARM_HOUR:ALARM_MINUTE today or tomorrow (if that time already passed).
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, ALARM_HOUR);
        calendar.set(Calendar.MINUTE, ALARM_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        long triggerAt = calendar.getTimeInMillis();

        // Cancel any existing alarm before setting the next one (exact alarms are one-shot).
        alarmManager.cancel(pendingIntent);

        // Exact alarm: fires at the specified time instead of being deferred (Doze/battery).
        // On API 31+ check canScheduleExactAlarms(); handle SecurityException and fall back to inexact.
        boolean scheduled = false;
        try {
            if (alarmManager.canScheduleExactAlarms()) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                    scheduled = true;
                } catch (SecurityException e) {
                    Log.w("SyncTriggered", "Exact alarm not allowed, trying setExact", e);
                }
            }
            if (!scheduled) {
                try {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                    scheduled = true;
                } catch (SecurityException e) {
                    Log.w("SyncTriggered", "setExact not allowed, using inexact set()", e);
                }
            }
            if (!scheduled) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                scheduled = true;
            }
        } catch (Exception e) {
            Log.e("SyncTriggered", "Failed to schedule daily fetch, using inexact fallback", e);
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                scheduled = true;
            } catch (Exception e2) {
                Log.e("SyncTriggered", "Could not set daily fetch alarm", e2);
            }
        }
        if (scheduled) {
            Log.i("App", "Alarm scheduled for " + ALARM_HOUR + ":" + String.format("%02d", ALARM_MINUTE));
        }
    }
}
