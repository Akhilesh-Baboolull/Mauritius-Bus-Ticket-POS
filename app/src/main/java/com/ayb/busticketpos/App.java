package com.ayb.busticketpos;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Application entry. Two processes: default (main) and :bg (location).
 * Debug: Log tags "App", "SyncTriggered" (alarms), "FetchReceiver", "UpdateFallback", "UpdateWorker", "LocationFG".
 */
public class App extends Application {
    /** Daily alarm time for DB fetch + update check (change here for tests, e.g. 18 and 30 for 18:30). */
    private static final int ALARM_HOUR = 21;
    private static final int ALARM_MINUTE = 0;
    /** Same-day fallback: if 21:00 didn't run, run once by 23:59 so we get exactly one check per day. */
    private static final int FALLBACK_HOUR = 23;
    private static final int FALLBACK_MINUTE = 59;

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
            scheduleDailyFetchFallback(this);
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
        // Clean up old finished/cancelled WorkManager jobs.
        try {
            WorkManager.getInstance(this).pruneWork();
        } catch (Exception e) {
            Log.w("App", "WorkManager prune failed", e);
        }
        // Start location service unconditionally so it runs even if WorkManager threw (e.g. on new install).
        try {
            LocationForegroundService.startIfNeeded(this);
        } catch (Exception e) {
            Log.w("App", "LocationForegroundService start failed", e);
        }

        scheduleDailyFetch(this);
        scheduleDailyFetchFallback(this);

        clearUpdateFlagIfInstalled(this);

        // Launch fallback: if no check in 20+ hours (e.g. first install or missed alarm), check only.
        // Installing stays with the 21:00 / 23:59 alarms and Settings "Install now".
        long lastUpdateCheck = Prefs.getLastUpdateCheckAt(this);
        if (lastUpdateCheck == 0L || System.currentTimeMillis() - lastUpdateCheck > 20L * 3600 * 1000) {
            try {
                UpdateScheduler.enqueueCheckOnly(this, true);
                Log.d("App", "Launch fallback: enqueued check-only update");
            } catch (Exception e) {
                Log.w("App", "Launch fallback enqueue failed", e);
            }
        }

        // Backup for 21:00 alarm: if the alarm is cleared (OEM/Doze), this runs every 24h and enqueues update if last check > 20h.
        try {
            PeriodicWorkRequest backupReq = new PeriodicWorkRequest.Builder(
                    DailyUpdateBackupWorker.class, 24, TimeUnit.HOURS)
                    .setConstraints(new androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build();
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "daily-update-backup",
                    ExistingPeriodicWorkPolicy.KEEP,
                    backupReq);
        } catch (Exception e) {
            Log.w("App", "Daily update backup schedule failed", e);
        }

        // After update relaunch: run DB sync once (update-first flow)
        if (Prefs.getAndClearRunDbSyncOnNextLaunch(this)) {
            try {
                AppDatabase.forceRefresh(this);
            } catch (Exception e) {
                android.util.Log.w("App", "DB sync on launch failed", e);
            }
        }

        SyncQueueMaintenance.onStartup(this);
        schedulePeriodicSyncDrain(this);
    }

    /** After an install, the stored "update available" target is at or below the running version. */
    private static void clearUpdateFlagIfInstalled(Context ctx) {
        try {
            if (!Prefs.isUpdateAvailable(ctx)) return;
            long installed = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0)
                    .getLongVersionCode();
            if (installed >= Prefs.getUpdateAvailableVersionCode(ctx)) {
                Prefs.clearUpdateAvailable(ctx);
                Prefs.setLastUpdateResult(ctx, "Updated to version " + installed);
                Log.i("App", "Update flag cleared (installed " + installed + ")");
            }
        } catch (Exception e) {
            Log.w("App", "clearUpdateFlagIfInstalled failed", e);
        }
    }

    /** Every 6h when online, drain any backlog (e.g. after multi-day offline). */
    private static void schedulePeriodicSyncDrain(Context context) {
        try {
            PeriodicWorkRequest drainReq = new PeriodicWorkRequest.Builder(
                    SyncWorker.class, 6, TimeUnit.HOURS)
                    .setConstraints(new androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build();
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "sync-drain-periodic",
                    ExistingPeriodicWorkPolicy.KEEP,
                    drainReq);
        } catch (Exception e) {
            Log.w("App", "Periodic sync drain schedule failed", e);
        }
    }

    /** True if we are in the :bg process (location sync). When process name is null/empty (e.g. some OEMs at boot), treat as :bg to avoid running WorkManager/main init in :bg and crashing. */
    public static boolean isBgProcess(Context ctx) {
        String processName = getProcessNameSafe(ctx);
        if (processName == null || processName.isEmpty()) return false; // unknown => treat as main
        return processName.endsWith(":bg");
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

        // Cancel any existing alarm before setting the next one (one-shot).
        alarmManager.cancel(pendingIntent);

        // Prefer setAlarmClock(): not deferred in Doze, most reliable for daily 21:00. Min SDK 32.
        // Fallback: setExactAndAllowWhileIdle / setExact / set().
        boolean scheduled = false;
        try {
            try {
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerAt, null);
                alarmManager.setAlarmClock(info, pendingIntent);
                scheduled = true;
                Log.d("SyncTriggered", "Using setAlarmClock for daily update");
            } catch (Exception e) {
                Log.w("SyncTriggered", "setAlarmClock failed, trying exact alarm", e);
            }
            if (!scheduled) {
                try {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                        scheduled = true;
                    }
                } catch (SecurityException e) {
                    Log.w("SyncTriggered", "setExactAndAllowWhileIdle not allowed", e);
                }
            }
            if (!scheduled) {
                try {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                    scheduled = true;
                } catch (Exception e) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                    scheduled = true;
                }
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

    /**
     * Schedule the 23:59 same-day fallback alarm. If the 21:00 alarm didn't fire, this ensures we
     * still run one update check before midnight. One-shot; reschedule from UpdateFallbackReceiver.
     */
    public static void scheduleDailyFetchFallback(Context context) {
        Context ctx = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(ctx, UpdateFallbackReceiver.class);
        intent.setPackage(ctx.getPackageName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                ctx, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, FALLBACK_HOUR);
        calendar.set(Calendar.MINUTE, FALLBACK_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        long triggerAt = calendar.getTimeInMillis();

        alarmManager.cancel(pendingIntent);
        try {
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerAt, null);
            alarmManager.setAlarmClock(info, pendingIntent);
            Log.d("App", "23:59 fallback alarm scheduled");
        } catch (Exception e) {
            try {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                }
                Log.d("App", "23:59 fallback alarm scheduled (fallback method)");
            } catch (Exception e2) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        }
    }
}
