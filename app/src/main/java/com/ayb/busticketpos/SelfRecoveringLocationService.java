// SelfRecoveringLocationService.java
package com.ayb.busticketpos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.location.*;

public class SelfRecoveringLocationService extends Service {

    public static final String CHANNEL_ID = "ayb_location_channel";
    private static final int NOTIF_ID = 1011;

    private FusedLocationProviderClient fused;
    private LocationCallback callback;

    // restart constants
    private static final int RESTART_REQUEST_CODE = 9901;
    private static final long RESTART_DELAY_MS = 2 * 60_000L; // 2 minutes

    /** one-liner helper */
    public static void startIfNeeded(Context ctx) {
        Intent i = new Intent(ctx, SelfRecoveringLocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ContextCompat.startForegroundService(ctx, i);
        else
            ctx.startService(i);
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, SelfRecoveringLocationService.class));
    }

    // called first thing after process restarts
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createChannel();

            Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("AYB Way – Location active")
                    .setContentText("Tracking bus position")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setOngoing(true)
                    .build();

            startForeground(NOTIF_ID, notif);
            fused = LocationServices.getFusedLocationProviderClient(this);
            startLocationUpdates();

            Log.i("SelfRecoveringService", "Service created & foreground started");
        } catch (Exception e) {
            Log.e("SelfRecoveringService", "Error in onCreate: " + e);
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        LocationRequest req = new LocationRequest.Builder(300_000L)
                .setMinUpdateIntervalMillis(300_000L)
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                // TODO: postLocation(result.getLastLocation());
            }
        };
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("SelfRecoveringService", "onStartCommand");
        // Keep running even if system kills and recreates
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.w("SelfRecoveringService", "Service destroyed – scheduling restart");
        try {
            if (fused != null && callback != null)
                fused.removeLocationUpdates(callback);
        } catch (Exception ignored) {}

        scheduleSelfRestart(); // ❤️ key line
    }

    /** Relaunch the service after a delay, even in Doze */
    @SuppressLint("ScheduleExactAlarm")
/** Relaunch the service after a delay, even in Doze */
    private void scheduleSelfRestart() {
        try {
            Intent i = new Intent(getApplicationContext(), SelfRecoveringLocationService.class);
            PendingIntent pi = PendingIntent.getService(
                    getApplicationContext(),
                    RESTART_REQUEST_CODE,
                    i,
                    PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            long trigger = System.currentTimeMillis() + RESTART_DELAY_MS;

            if (am != null) {
                // 👇 Cancel any previously scheduled restart before setting a new one
                am.cancel(pi);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
                }
            }

            Log.i("SelfRecoveringService", "Restart scheduled in " + (RESTART_DELAY_MS / 1000) + "s");
        } catch (Exception e) {
            Log.e("SelfRecoveringService", "Failed to schedule restart: " + e);
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Location Service",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Background location tracking");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }
}
