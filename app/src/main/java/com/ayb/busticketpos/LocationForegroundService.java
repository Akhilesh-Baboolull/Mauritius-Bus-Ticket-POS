// LocationForegroundService.java
package com.ayb.busticketpos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class LocationForegroundService extends Service {

    public static final String CHANNEL_ID = "ayb_location_channel";

    // NEW: special action to force an immediate send
    private static final String ACTION_SEND_NOW = "com.ayb.busticketpos.action.SEND_NOW";
    private static final String ACTION_SEND_DAY_END = "com.ayb.busticketpos.action.SEND_DAY_END";

    private FusedLocationProviderClient fused;
    private LocationCallback callback;

    // at class top
    private static final int MODE_DAY_ACTIVE = 1;   // 30s
    private static final int MODE_IDLE       = 0;   // 5min

    private int lastAppliedMode = -1;               // unknown at start



    /** Start normal foreground tracking (same as before) */
    public static void startIfNeeded(Context ctx) {
        Intent i = new Intent(ctx, LocationForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    /** Stop service */
    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, LocationForegroundService.class));
    }

    /** NEW: Trigger a one-shot send immediately (no waiting for the 60s interval) */
    public static void sendNow(Context ctx) {
        Intent i = new Intent(ctx, LocationForegroundService.class).setAction(ACTION_SEND_NOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    @Override
    public void onCreate() {
        super.onCreate();

        try {
            // ✅ Step 1: Create notification channel first
            createChannel();

            // ✅ Step 2: Immediately call startForeground() — must be within 5 seconds
            Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("AYB Way - Location active")
                    .setContentText("Sending bus position every minute")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();
            startForeground(1011, notif);

        } catch (Exception e) {
            android.util.Log.e("LocationService", "Failed to start foreground safely: " + e.getMessage(), e);
            // If foreground start fails, stop the service to prevent system crash
            stopSelf();
            return;
        }

        try {
            // ✅ Step 3: Initialize fused location client safely
            fused = LocationServices.getFusedLocationProviderClient(this);
            applyModeFromPrefs();
        } catch (Throwable t) {
            android.util.Log.e("LocationService", "FusedLocation init failed: " + t.getMessage(), t);
        }

        // ✅ Step 4: Watchdog for periodic forced sends (every 5 minutes)
        Handler watchdog = new Handler(getMainLooper());
        watchdog.postDelayed(new Runnable() {
            @SuppressLint({"ScheduleExactAlarm", "MissingPermission"})
            @Override
            public void run() {
                try {
                    if (hasPerms()) {
                        sendImmediateOnce();
                    } else {
                        android.util.Log.w("LocationService", "Watchdog skipped: missing location permission");
                    }
                } catch (Exception e) {
                    android.util.Log.e("LocationService", "Watchdog exception: " + e.getMessage(), e);
                }
                // Re-schedule itself
                watchdog.postDelayed(this, 300_000L);
            }
        }, 300_000L);

        // ✅ Step 5: Heartbeat loop with wake-lock protection
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) {
                android.util.Log.w("LocationService", "PowerManager null — skipping heartbeat setup");
                return;
            }

            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AYB:Heartbeat");
            Handler h = new Handler(getMainLooper());
            final Runnable[] heartbeat = new Runnable[1];

            heartbeat[0] = () -> {
                try {
                    wl.acquire(30_000L); // hold for 30 seconds max
                    if (hasPerms()) {
                        sendImmediateOnce();
                    } else {
                        android.util.Log.w("LocationService", "Heartbeat skipped: missing location permission");
                    }
                } catch (SecurityException se) {
                    android.util.Log.e("LocationService", "SecurityException during heartbeat send: " + se.getMessage());
                } catch (Throwable t) {
                    android.util.Log.e("LocationService", "Heartbeat exception: " + t.getMessage(), t);
                } finally {
                    if (wl.isHeld()) wl.release();
                }

                // Re-schedule every 5 minutes
                h.postDelayed(heartbeat[0], 300_000L);
            };

            // Start heartbeat loop after 5 minutes
            h.postDelayed(heartbeat[0], 300_000L);

        } catch (Throwable t) {
            android.util.Log.e("LocationService", "Heartbeat setup failed: " + t.getMessage(), t);
        }

        android.util.Log.i("LocationService", "Foreground service created successfully");
    }


    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    private void applyModeFromPrefs() {
        int modeNow = (Prefs.getDayStatus(this) == 1) ? MODE_DAY_ACTIVE : MODE_IDLE;
        if (modeNow != lastAppliedMode) {
            startLocationUpdatesForMode(modeNow);
        }
    }


    /** NEW: handle ACTION_SEND_NOW to perform one-shot sending */
    @SuppressLint({"MissingPermission", "ScheduleExactAlarm"})
    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_SEND_NOW.equals(action)) {
                sendImmediateOnce();  // respect dayStatus
            } else if (ACTION_SEND_DAY_END.equals(action)) {
                sendImmediateOnce();   // override dayStatus
            }
        }
        // Also re-check mode in case Prefs changed while we were stopped/restarted
        applyModeFromPrefs();
        return START_STICKY;
    }


    public static void sendOnDayEnd(Context ctx) {
        Intent i = new Intent(ctx, LocationForegroundService.class)
                .setAction("com.ayb.busticketpos.action.SEND_DAY_END");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    private void startLocationUpdatesForMode(int mode) {
        if (!hasPerms()) { stopSelf(); return; }

        // ⏱️ Update intervals
        long intervalMs = (mode == MODE_DAY_ACTIVE) ? 30_000L : 300_000L; // 30s vs 5min

        // ⚙️ Choose priority based on mode
        int priority = (mode == MODE_DAY_ACTIVE)
                ? Priority.PRIORITY_HIGH_ACCURACY       // 🚀 More reliable during trips
                : Priority.PRIORITY_LOW_POWER;           // 🌙 Save battery during idle

        // 🧭 Build request dynamically
        LocationRequest req = new LocationRequest.Builder(intervalMs)
                .setMinUpdateIntervalMillis(intervalMs)
                .setPriority(priority)
                .build();

        // 👂 Callback creation or reuse
        if (callback == null) {
            callback = new LocationCallback() {
                @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
                @Override public void onLocationResult(LocationResult result) {
                    if (result == null) return;

                    Location loc = result.getLastLocation();
                    if (loc != null) postLocation(loc);

                    // 🔁 Re-evaluate mode in case "day" status changed
                    applyModeFromPrefs();
                }
            };
        } else {
            // Remove old updates before re-registering with new interval/priority
            fused.removeLocationUpdates(callback);
        }

        fused.requestLocationUpdates(req, callback, getMainLooper());
        lastAppliedMode = mode;

        android.util.Log.d("LocationService", "Started location updates. Mode="
                + (mode == MODE_DAY_ACTIVE ? "DAY_ACTIVE" : "IDLE")
                + ", priority=" + priority);
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fused != null && callback != null) {
            fused.removeLocationUpdates(callback);
            callback = null; // 🧹 allow GC to reclaim memory
        }
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Location", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Background location for bus positioning");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

    /** NEW: one-shot get + send (no timer) */
    @SuppressLint("ScheduleExactAlarm")
    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.SCHEDULE_EXACT_ALARM})
    /** one-shot get + send (dayEnd = true overrides status) */
    private void sendImmediateOnce() {

        if (!hasPerms()) return;

        CancellationTokenSource cts = new CancellationTokenSource();
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.getToken())
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        postLocation(loc);
                    } else {
                        fused.getLastLocation().addOnSuccessListener(last -> {
                            if (last != null) postLocation(last);
                        });
                    }
                });

        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(this, LocationForegroundService.class).setAction(ACTION_SEND_NOW);
        PendingIntent pi = PendingIntent.getService(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5 * 60 * 1000, pi);

    }


    /** Build payload + send via PositioningClient */
    private void postLocation(Location loc) {
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new java.util.Date());

        String tenantStr  = PrefsSecure.getTenantId(this);
        String machineStr = PrefsSecure.getMachineId(this);
        String busNo      = Prefs.getSelectedBus(this);
        if (busNo == null) busNo = null; // server requires non-empty; empty will fail fast

        String currentRoute = Prefs.getSelectedRouteName(this);
        if (currentRoute != null && currentRoute.trim().isEmpty()) currentRoute = null;

        Integer tripNo = null;
        try {
            int t = Prefs.getTripCount(this);
            if (t > 0) tripNo = t;
        } catch (Exception ignored) {}

        int tenantId, machineId;
        try {
            tenantId  = Integer.parseInt(tenantStr);
            machineId = Integer.parseInt(machineStr);
        } catch (Exception e) {
            return; // not registered
        }

        String latLng = loc.getLatitude() + ", " + loc.getLongitude();

        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        PositioningClient.send(
                this,
                date, tenantId, machineId, busNo,
                latLng,
                currentRoute,
                tripNo,
                batteryPct,
                null // no callback needed
        );
    }

    private boolean hasPerms() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
