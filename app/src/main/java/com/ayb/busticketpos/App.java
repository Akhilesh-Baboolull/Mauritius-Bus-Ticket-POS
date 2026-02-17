package com.ayb.busticketpos;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
        MemoryWatchdog.init(this);
        Prefs.setLastAlive(this, System.currentTimeMillis());

        // Clean up old finished/cancelled WorkManager jobs
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

    /**
     * Schedule the next daily fetch/update alarm at ALARM_HOUR:ALARM_MINUTE (today or tomorrow).
     * Uses an exact alarm (setExactAndAllowWhileIdle / setExact) so it fires on time even in Doze;
     * setRepeating() was inexact and could delay by an hour or more. Call from Application.onCreate()
     * and from FetchReceiver after it runs so the next day is scheduled (exact alarms are one-shot).
     */
    public static void scheduleDailyFetch(Context context) {
        Context ctx = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(ctx, FetchReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set trigger time to ALARM_HOUR:ALARM_MINUTE today or tomorrow (if that time already passed)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, ALARM_HOUR);
        calendar.set(Calendar.MINUTE, ALARM_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        long triggerAt = calendar.getTimeInMillis();
        // Cancel any existing alarm before setting the next one
        alarmManager.cancel(pendingIntent);

        // Exact alarm: fires at the specified time instead of being deferred (Doze/battery).
        // On API 31+ check canScheduleExactAlarms(); handle SecurityException and fall back to inexact.
        boolean scheduledExact = false;
        if (alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                scheduledExact = true;
            } catch (SecurityException e) {
                Log.w("SyncTriggered", "Exact alarm not allowed, using inexact fallback", e);
            }
        }
        if (!scheduledExact) {
            try {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } catch (SecurityException e) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        }

        Log.i("SyncTriggered", "Daily fetch scheduled for " + ALARM_HOUR + ":" + String.format("%02d", ALARM_MINUTE) + (scheduledExact ? " (exact alarm)" : " (inexact fallback)"));
    }
}
