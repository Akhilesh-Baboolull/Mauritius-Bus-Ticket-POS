package com.ayb.busticketpos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.BatteryManager;
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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** SEND_NOW / SEND_DAY_END can arrive before deferred FusedLocation init; replay when ready. */
    private volatile boolean pendingSendNow = false;
    private volatile boolean pendingSendDayEnd = false;
    @Nullable private volatile PositioningSnapshot pendingSnapshot = null;
    @Nullable private volatile PositioningSnapshot immediateSnapshot = null;

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
        try {
            Context app = ctx.getApplicationContext();
            Intent i = new Intent(app, LocationForegroundService.class);
            app.startForegroundService(i);
        } catch (Exception e) {
            android.util.Log.e("LocationFG", "start failed", e);
        }
    }

    public static void stop(Context ctx) {
        Context app = ctx.getApplicationContext();
        Intent i = new Intent(app, LocationForegroundService.class).setAction(ACTION_STOP);
        app.startForegroundService(i);
    }

    public static void sendNow(Context ctx) {
        sendNow(ctx, null);
    }

    /** @param snapshot operational fields from main process (required for correct bus/route/trip in :bg). */
    public static void sendNow(Context ctx, @Nullable PositioningSnapshot snapshot) {
        Context app = ctx.getApplicationContext();
        Intent i = new Intent(app, LocationForegroundService.class).setAction(ACTION_SEND_NOW);
        if (snapshot != null) {
            snapshot.attachTo(i);
        }
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
        try {
            ensureChannel();
            startForeground(NOTIF_ID, buildNotif("Tracking enabled"));
            Log.i(TAG, "Foreground notification posted; deferring location client init");
        } catch (Exception e) {
            Log.e(TAG, "onCreate: failed to start foreground", e);
            stopSelf();
            return;
        }

        // Defer the rest so onCreate() returns quickly; avoids ForegroundServiceDidNotStartInTimeException.
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Log.d(TAG, "Deferred init: creating FusedLocationProviderClient and applying mode");
                fused = LocationServices.getFusedLocationProviderClient(this);
                callback = new LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull LocationResult locationResult) {
                        try {
                            Location loc = locationResult.getLastLocation();
                            if (loc == null) return;
                            postLocation(loc, false, false);
                            applyModeFromDbIfNeeded(false);
                        } catch (Exception e) {
                            Log.e(TAG, "onLocationResult failed", e);
                        }
                    }
                };
                applyModeFromDbIfNeeded(true);
                flushPendingImmediateSends();
            } catch (Exception e) {
                Log.e(TAG, "Deferred init failed", e);
            }
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
                PositioningSnapshot snap = PositioningSnapshot.fromIntent(intent);
                Log.d(TAG, "onStartCommand: ACTION_SEND_NOW snapshot=" + snap);
                requestImmediateSend(false, snap);
            }

            if (ACTION_SEND_DAY_END.equals(action)) {
                Log.d(TAG, "onStartCommand: ACTION_SEND_DAY_END — day ended ping");
                requestImmediateSend(true, null);
            }
        }

        try { applyModeFromDbIfNeeded(false); } catch (Exception e) { Log.e(TAG, "applyModeFromDbIfNeeded failed", e); }

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
            try {
                if (fused == null || callback == null) return;
                boolean dayActive = BgStateRepo.isDayActive(getApplicationContext());
                if (modeAppliedOnce && dayActive != lastDayActive) invalidateStateCache();
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
            } catch (Exception e) {
                Log.e(TAG, "applyModeFromDbIfNeeded worker failed", e);
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

    private void requestImmediateSend(boolean dayEnded, @Nullable PositioningSnapshot snapshot) {
        invalidateStateCache();
        immediateSnapshot = snapshot;
        if (fused == null) {
            if (dayEnded) {
                pendingSendDayEnd = true;
                pendingSnapshot = null;
            } else {
                pendingSendNow = true;
                pendingSnapshot = snapshot;
            }
            Log.d(TAG, "Immediate send queued (location client not ready) dayEnded=" + dayEnded);
            return;
        }
        try {
            sendImmediateOnce(true, dayEnded, snapshot);
        } catch (Exception e) {
            Log.e(TAG, "requestImmediateSend failed dayEnded=" + dayEnded, e);
        }
    }

    private void flushPendingImmediateSends() {
        if (fused == null) return;
        final boolean sendNow = pendingSendNow;
        final boolean sendDayEnd = pendingSendDayEnd;
        final PositioningSnapshot snap = pendingSnapshot;
        pendingSendNow = false;
        pendingSendDayEnd = false;
        pendingSnapshot = null;
        if (!sendNow && !sendDayEnd) return;
        Log.i(TAG, "Flushing pending immediate send(s) now=" + sendNow + " dayEnd=" + sendDayEnd);
        mainHandler.post(() -> {
            try {
                if (sendNow) {
                    sendImmediateOnce(true, false, snap);
                }
                if (sendDayEnd) {
                    invalidateStateCache();
                    sendImmediateOnce(true, true, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "flushPendingImmediateSends failed", e);
            }
        });
    }

    private void sendImmediateOnce(boolean forceRefreshState, boolean dayEnded,
                                   @Nullable PositioningSnapshot snapshot) {
        if (fused == null) {
            requestImmediateSend(dayEnded, snapshot);
            return;
        }
        immediateSnapshot = snapshot;
        try {
            CancellationTokenSource cts = new CancellationTokenSource();
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.getToken())
                    .addOnSuccessListener(loc -> {
                        try {
                            if (loc != null) {
                                postLocation(loc, forceRefreshState, dayEnded);
                            } else {
                                fused.getLastLocation().addOnSuccessListener(last -> {
                                    try {
                                        if (last != null) postLocation(last, forceRefreshState, dayEnded);
                                    } catch (Exception e) {
                                        Log.e(TAG, "getLastLocation callback failed", e);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "sendImmediateOnce callback failed", e);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "sendImmediateOnce failed", e);
        }
    }

    // ---------------------------------------------------------------------
    // Payload send: shape fields exactly as your server rules require
    // ---------------------------------------------------------------------

    private void postLocation(Location loc, boolean forceRefreshState, boolean dayEnded) {
        if (loc == null) return;
        final String tenantStr  = PrefsSecure.getTenantId(this);
        final String machineStr = PrefsSecure.getMachineId(this);
        final String latLng = loc.getLatitude() + ", " + loc.getLongitude();
        final int batteryPct = getBatteryPct();

        io.execute(() -> {
            try {
                int tenantId, machineId;
                try {
                    tenantId  = Integer.parseInt(tenantStr != null ? tenantStr.trim() : "0");
                    machineId = Integer.parseInt(machineStr != null ? machineStr.trim() : "0");
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid tenant/machine id, skip send");
                    return;
                }

                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                PositioningSnapshot snap = immediateSnapshot;
                immediateSnapshot = null;

                final boolean dayActive;
                final boolean tripActive;
                final String busNo;
                final String route;
                final Integer tripNo;

                if (dayEnded) {
                    dayActive = false;
                    tripActive = false;
                    busNo = null;
                    route = null;
                    tripNo = null;
                } else if (snap != null) {
                    dayActive = snap.dayActive;
                    tripActive = snap.tripActive;
                    busNo = snap.dayActive ? snap.busNo : null;
                    route = (snap.dayActive && snap.tripActive) ? snap.currentRoute : null;
                    tripNo = (snap.dayActive && snap.tripActive) ? snap.tripNo : null;
                } else {
                    BgState st = getState(forceRefreshState);
                    dayActive = st.dayActive;
                    tripActive = st.tripActive;
                    busNo = !dayActive ? null : st.busNo;
                    route = (!dayActive || !tripActive) ? null : st.currentRoute;
                    tripNo = (!dayActive || !tripActive) ? null : st.tripNo;
                }

                Log.i(TAG, "positioning payload bus=" + busNo + " route=" + route + " tripNo=" + tripNo
                        + " dayEnded=" + dayEnded + " usedSnapshot=" + (snap != null));

                PositioningClient.send(
                        getApplicationContext(),
                        date,
                        tenantId,
                        machineId,
                        busNo,
                        latLng,
                        route,
                        tripNo,
                        batteryPct,
                        null
                );
            } catch (Exception e) {
                Log.e(TAG, "postLocation worker failed", e);
            }
        });
    }

    // ---------------------------------------------------------------------
    // State cache
    // ---------------------------------------------------------------------

    private BgState getState(boolean forceRefresh) {
        try {
            long now = System.currentTimeMillis();
            if (!forceRefresh) {
                BgState st = cachedState;
                if (st != null && (now - cachedAtMs) <= STATE_CACHE_MS) return st;
            }
            BgState fresh = BgStateRepo.read(getApplicationContext());
            cachedState = fresh;
            cachedAtMs = now;
            return fresh;
        } catch (Exception e) {
            Log.e(TAG, "getState failed", e);
            return BgState.inactive();
        }
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
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                    CH_ID,
                    "Background Location",
                    NotificationManager.IMPORTANCE_LOW
            );
            nm.createNotificationChannel(ch);
        } catch (Exception e) {
            Log.e(TAG, "ensureChannel failed", e);
        }
    }

    private Notification buildNotif(String text) {
        try {
            return new NotificationCompat.Builder(this, CH_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Bus Ticket POS")
                    .setContentText(text)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "buildNotif failed", e);
            return new NotificationCompat.Builder(this, CH_ID).setContentTitle("Bus Ticket POS").setContentText(text).build();
        }
    }
}
