package com.ayb.busticketpos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationForegroundService extends Service {

    private static final String TAG = "LocationFG";
    private static final String CH_ID = "loc_fg";
    private static final int NOTIF_ID = 101;

    // Keep your existing actions/method names
    private static final String ACTION_SEND_NOW     = "com.ayb.busticketpos.action.SEND_NOW";
    private static final String ACTION_SEND_DAY_END = "com.ayb.busticketpos.action.SEND_DAY_END";
    private static final String ACTION_STOP         = "com.ayb.busticketpos.action.STOP";

    private FusedLocationProviderClient fused;
    private LocationCallback callback;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // mode = dayActive? (30s/5min)
    private boolean lastDayActive = false;
    private boolean modeAppliedOnce = false;

    // Cache “state” so payload stays stable until status changes,
    // BUT allow forced refresh for SEND_NOW / SEND_DAY_END.
    private volatile BgState cachedState = null;
    private volatile long cachedAtMs = 0L;
    private static final long STATE_CACHE_MS = 15_000L; // stable + low DB load

    // ---------------------------------------------------------------------
    // DROP-IN PUBLIC API (existing call sites)
    // ---------------------------------------------------------------------

    public static void startIfNeeded(Context ctx) {
        // Minimum-change: always start; service chooses 30s/5min from DB.
        start(ctx);
    }

    public static void start(Context ctx) {
        Context app = ctx.getApplicationContext();
        Intent i = new Intent(app, LocationForegroundService.class);
        app.startForegroundService(i);
    }

    public static void stop(Context ctx) {
        Context app = ctx.getApplicationContext();
        Intent i = new Intent(app, LocationForegroundService.class).setAction(ACTION_STOP);
        app.startForegroundService(i);
    }

    public static void sendNow(Context ctx) {
        Context app = ctx.getApplicationContext();
        Intent i = new Intent(app, LocationForegroundService.class).setAction(ACTION_SEND_NOW);
        app.startForegroundService(i);
    }

    public static void sendOnDayEnd(Context ctx) {
        Context app = ctx.getApplicationContext();
        Intent i = new Intent(app, LocationForegroundService.class).setAction(ACTION_SEND_DAY_END);
        app.startForegroundService(i);
    }

    // ---------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        // Call startForeground() immediately so we satisfy the timeout when :bg process cold-starts.
        ensureChannel();
        startForeground(NOTIF_ID, buildNotif("Tracking enabled"));
        Log.i(TAG, "Foreground notification posted; deferring location client init");

        // Defer the rest so onCreate() returns quickly; avoids ForegroundServiceDidNotStartInTimeException.
        new Handler(Looper.getMainLooper()).post(() -> {
            Log.d(TAG, "Deferred init: creating FusedLocationProviderClient and applying mode");
            fused = LocationServices.getFusedLocationProviderClient(this);
            callback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    Location loc = locationResult.getLastLocation();
                    if (loc == null) return;

                    // Normal periodic sends: cached state is ok (stable payload).
                    postLocation(loc, false);

                    // Update mode if DB day status changed.
                    applyModeFromDbIfNeeded(false);
                }
            };
            applyModeFromDbIfNeeded(true);
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Ensure we're in foreground (covers late process start or retries).
        try {
            startForeground(NOTIF_ID, buildNotif("Tracking enabled"));
            Log.d(TAG, "onStartCommand: foreground notification ensured");
        } catch (Exception e) {
            Log.w(TAG, "onStartCommand: startForeground failed", e);
        }

        // Handle action only when present (first start often has null intent/action).
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();

            if (ACTION_STOP.equals(action)) {
                Log.i(TAG, "onStartCommand: ACTION_STOP — stopping service");
                stopSelfSafely();
                return START_NOT_STICKY;
            }

            if (ACTION_SEND_NOW.equals(action)) {
                Log.d(TAG, "onStartCommand: ACTION_SEND_NOW — force refresh and send once");
                // Force refresh state (don’t use cached payload)
                sendImmediateOnce(true);
            }

            if (ACTION_SEND_DAY_END.equals(action)) {
                Log.d(TAG, "onStartCommand: ACTION_SEND_DAY_END — force refresh (day ended)");
                // Day just ended: force refresh so busNo/tripNo/route are nulled correctly.
                sendImmediateOnce(true);
            }
        }

        // In case DB day status changed while service was alive, re-apply mode.
        applyModeFromDbIfNeeded(false);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (fused != null && callback != null) fused.removeLocationUpdates(callback); } catch (Exception ignored) {}
        io.shutdown();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ---------------------------------------------------------------------
    // DB-driven mode logic (30s when day active, else 5 min)
    // ---------------------------------------------------------------------

    private void applyModeFromDbIfNeeded(boolean force) {
        io.execute(() -> {
            if (fused == null || callback == null) return; // Deferred init not done yet
            boolean dayActive = BgStateRepo.isDayActive(getApplicationContext());

            // Mode changes = invalidate cached payload so it updates quickly
            if (modeAppliedOnce && dayActive != lastDayActive) {
                invalidateStateCache();
            }

            if (!force && modeAppliedOnce && dayActive == lastDayActive) return;

            lastDayActive = dayActive;
            modeAppliedOnce = true;

            LocationRequest req = buildRequest(dayActive);

            try { fused.removeLocationUpdates(callback); } catch (Exception ignored) {}

            try {
                fused.requestLocationUpdates(req, callback, getMainLooper());
                Log.d(TAG, "Mode applied dayActive=" + dayActive + " interval=" + (dayActive ? "30s" : "5min"));
            } catch (SecurityException se) {
                Log.e(TAG, "Missing location permission", se);
            }
        });
    }

    private LocationRequest buildRequest(boolean dayActive) {
        long intervalMs = dayActive ? 30_000L : 300_000L;

        int priority = dayActive
                ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;

        return new LocationRequest.Builder(priority, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs)
                // allow some batching when inactive for better battery
                .setMaxUpdateDelayMillis(dayActive ? intervalMs : 2 * intervalMs)
                .build();
    }

    // ---------------------------------------------------------------------
    // One-shot send (prefer current location)
    // ---------------------------------------------------------------------

    private void sendImmediateOnce(boolean forceRefreshState) {
        if (fused == null) return; // Deferred init not done yet
        try {
            CancellationTokenSource cts = new CancellationTokenSource();
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.getToken())
                    .addOnSuccessListener(loc -> {
                        if (loc != null) {
                            postLocation(loc, forceRefreshState);
                        } else {
                            fused.getLastLocation().addOnSuccessListener(last -> {
                                if (last != null) postLocation(last, forceRefreshState);
                            });
                        }
                    });
        } catch (SecurityException ignored) {}
    }

    // ---------------------------------------------------------------------
    // Payload send: shape fields exactly as your server rules require
    // ---------------------------------------------------------------------

    private void postLocation(Location loc, boolean forceRefreshState) {

        final String tenantStr  = PrefsSecure.getTenantId(this);
        final String machineStr = PrefsSecure.getMachineId(this);

        final String latLng = loc.getLatitude() + ", " + loc.getLongitude();
        final int batteryPct = getBatteryPct();

        io.execute(() -> {
            int tenantId, machineId;
            try {
                tenantId  = Integer.parseInt(tenantStr);
                machineId = Integer.parseInt(machineStr);
            } catch (Exception e) {
                return;
            }

            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            BgState st = getState(forceRefreshState);

            // ✅ Apply your exact rules:
            final boolean dayActive  = st.dayActive;
            final boolean tripActive = st.tripActive;

            final String busNo;
            final String route;
            final Integer tripNo;

            if (!dayActive) {
                // Day inactive: only core fields
                busNo = null;
                route = null;
                tripNo = null;
            } else if (!tripActive) {
                // Day active, trip inactive: include bus only
                busNo = st.busNo;
                route = null;
                tripNo = null;
            } else {
                // Trip active: include bus + route + tripNo
                busNo = st.busNo;
                route = st.currentRoute;
                tripNo = st.tripNo;
            }

            // Debug (optional)
            // Log.d("BUS_DEBUG", "payloadState day=" + dayActive + " trip=" + tripActive +
            //         " bus=[" + busNo + "] route=[" + route + "] tripNo=[" + tripNo + "]");

            PositioningClient.send(
                    getApplicationContext(),
                    date,
                    tenantId,
                    machineId,
                    busNo,        // null when day inactive
                    latLng,
                    route,        // null unless trip active
                    tripNo,       // null unless trip active
                    batteryPct,
                    null
            );
        });
    }

    // ---------------------------------------------------------------------
    // State cache
    // ---------------------------------------------------------------------

    private BgState getState(boolean forceRefresh) {
        long now = System.currentTimeMillis();

        if (!forceRefresh) {
            BgState st = cachedState;
            if (st != null && (now - cachedAtMs) <= STATE_CACHE_MS) return st;
        }

        BgState fresh = BgStateRepo.read(getApplicationContext());
        cachedState = fresh;
        cachedAtMs = now;
        return fresh;
    }

    private void invalidateStateCache() {
        cachedState = null;
        cachedAtMs = 0L;
    }

    private int getBatteryPct() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm == null) return 0;
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private void stopSelfSafely() {
        try { if (fused != null && callback != null) fused.removeLocationUpdates(callback); } catch (Exception ignored) {}
        stopForeground(true);
        stopSelf();
    }

    // ---------------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------------

    private void ensureChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel ch = new NotificationChannel(
                CH_ID,
                "Background Location",
                NotificationManager.IMPORTANCE_LOW
        );
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotif(String text) {
        return new NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // change to R.mipmap.ic_launcher if needed
                .setContentTitle("Bus Ticket POS")
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
