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
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLogger.init(this);

        Prefs.setLastAlive(this, System.currentTimeMillis());


        // 🧹 Clean up old finished/cancelled WorkManager jobs
        try {
            WorkManager.getInstance(this).pruneWork();
            LocationForegroundService.startIfNeeded(this);

        } catch (Exception e) {
            Log.w("App", "WorkManager prune failed", e);
        }

        scheduleDailyFetch();
    }

    private void startHeartbeatMonitor(Context ctx) {
        new Thread(() -> {
            while (true) {
                try {
                    Prefs.setLastAlive(ctx, System.currentTimeMillis());
                    Thread.sleep(60_000); // every 1 minute
                } catch (InterruptedException ignored) {}
            }
        }, "App-Heartbeat").start();
    }


    private void scheduleDailyFetch() {
        Context ctx = getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);

        Intent intent = new Intent(ctx, FetchReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set trigger time to 21:00 today or tomorrow
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 21);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1); // if past 21:00 today, schedule for tomorrow
        }

        // Cancel any old alarms and reschedule
        alarmManager.cancel(pendingIntent);

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );

        Log.i("SyncTriggered", "Daily DB fetch scheduled for 21:00");
    }

}
