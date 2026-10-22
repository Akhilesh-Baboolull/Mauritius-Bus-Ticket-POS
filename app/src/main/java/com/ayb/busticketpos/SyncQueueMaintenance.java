package com.ayb.busticketpos;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.work.WorkManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * Keeps the offline sync queue bounded and prioritizes tickets/day/trip over positioning.
 * POS devices may be offline for days; unbounded positioning retries caused DB bloat and OOM.
 */
public final class SyncQueueMaintenance {
    private static final String TAG = "SyncQueueMaint";

    /** All Room access must run here — never on the main thread. */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SyncQueueMaint");
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });

    /** Hard cap on rows in sync_queue (positioning is trimmed first). */
    public static final int MAX_QUEUE_ROWS = 400;
    /** Keep only the newest N positioning pings when backlogged. */
    public static final int MAX_POSITIONING_KEEP = 2;
    /** Drop stale positioning after this many failed drain attempts. */
    public static final int MAX_POSITIONING_RETRIES = 4;
    /** Business payloads (tickets, day, trip) — keep retrying longer. */
    public static final int MAX_BUSINESS_RETRIES = 80;
    /** Failed pushes older than this are dropped (rolling window). */
    public static final long MAX_QUEUE_AGE_MS = 24L * 60 * 60 * 1000;

    private static final long LOG_FILE_TRIM_BYTES = 256 * 1024;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SyncQueueMaintenance() {}

    public static boolean isOnline(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }

    /** Drop jobs older than 24h; on first run each calendar day, drop everything before midnight. */
    public static int purgeExpiredJobs(Context ctx) {
        int removed = 0;
        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            SyncJobDao dao = db.syncJobDao();
            long now = System.currentTimeMillis();
            removed += dao.deleteOlderThan(now - MAX_QUEUE_AGE_MS);

            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (!today.equals(Prefs.getSyncQueueDayPurgeKey(ctx))) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                removed += dao.deleteOlderThan(cal.getTimeInMillis());
                Prefs.setSyncQueueDayPurgeKey(ctx, today);
                Log.i(TAG, "Calendar-day queue purge removed=" + removed + " (before " + today + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "purgeExpiredJobs failed", e);
        }
        return removed;
    }

    /** Run before drain / on startup / when RAM is high. Returns rows removed. */
    public static int prune(Context ctx) {
        int removed = 0;
        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            SyncJobDao dao = db.syncJobDao();

            removed += purgeExpiredJobs(ctx);

            removed += dao.deleteOverRetries(ApiEndpoints.POSITIONING, MAX_POSITIONING_RETRIES);
            removed += dao.prunePositioningKeepLatest(ApiEndpoints.POSITIONING, MAX_POSITIONING_KEEP);

            int total = dao.count();
            if (total > MAX_QUEUE_ROWS) {
                int excess = total - MAX_QUEUE_ROWS;
                removed += dao.deleteOldestByEndpoint(ApiEndpoints.POSITIONING, excess);
                total = dao.count();
            }
            if (total > MAX_QUEUE_ROWS) {
                int excess = total - MAX_QUEUE_ROWS;
                removed += dao.deleteOldestAny(excess);
            }

            trimSyncFailLog(ctx);

            if (removed > 0) {
                Log.i(TAG, "Pruned sync_queue removed=" + removed + " remaining=" + dao.count());
            }
        } catch (Exception e) {
            Log.e(TAG, "prune failed", e);
        }
        return removed;
    }

    /** Lighter prune when memory watchdog warns. */
    public static void pruneAggressive(Context ctx) {
        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            SyncJobDao dao = db.syncJobDao();
            int removed = dao.deleteAllByEndpoint(ApiEndpoints.POSITIONING);
            removed += dao.deleteOverRetries(ApiEndpoints.POSITIONING, 1);
            dao.prunePositioningKeepLatest(ApiEndpoints.POSITIONING, 1);
            trimSyncFailLog(ctx);
            Log.w(TAG, "Aggressive prune removed=" + removed + " queue=" + dao.count());
        } catch (Exception e) {
            Log.e(TAG, "pruneAggressive failed", e);
        }
    }

    private static void trimSyncFailLog(Context ctx) {
        try {
            File f = new File(ctx.getApplicationContext().getFilesDir(), "sync_fail_log.csv");
            if (f.exists() && f.length() > LOG_FILE_TRIM_BYTES) {
                boolean ok = f.delete();
                Log.i(TAG, "Trimmed sync_fail_log.csv (" + ok + ") was " + f.length() + " bytes");
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Prune queue and schedule sync drain when online. Safe from Activity/BroadcastReceiver main thread.
     */
    /** Prune + optional drain after enqueue (enqueue insert may already be on a worker thread). */
    public static void runAfterEnqueue(Context appCtx, boolean scheduleDrain, boolean fastDrain) {
        EXEC.execute(() -> {
            try {
                prune(appCtx);
                if (scheduleDrain) {
                    SyncQueueUtil.requestDrain(appCtx, fastDrain);
                }
            } catch (Exception e) {
                Log.e(TAG, "runAfterEnqueue failed", e);
            }
        });
    }

    /**
     * Prune queue and schedule sync drain when online. Safe from Activity/BroadcastReceiver main thread.
     */
    public static void pruneAndScheduleDrainWhenOnline(Context ctx, boolean fastDrain) {
        final Context app = ctx.getApplicationContext();
        if (!isOnline(app)) return;
        EXEC.execute(() -> {
            try {
                prune(app);
                int pending = AppDatabase.getInstance(app).syncJobDao().count();
                if (pending > 0) {
                    Log.i(TAG, "Online: " + pending + " queued jobs — scheduling drain (fast=" + fastDrain + ")");
                    SyncQueueUtil.requestDrain(app, fastDrain || pending > 50);
                }
            } catch (Exception e) {
                Log.e(TAG, "pruneAndScheduleDrainWhenOnline failed", e);
            }
        });
    }

    public static void getQueueCountAsync(Context ctx, IntConsumer onMainThread) {
        final Context app = ctx.getApplicationContext();
        EXEC.execute(() -> {
            int count = 0;
            try {
                count = AppDatabase.getInstance(app).syncJobDao().count();
            } catch (Exception e) {
                Log.e(TAG, "getQueueCount failed", e);
            }
            int finalCount = count;
            MAIN.post(() -> onMainThread.accept(finalCount));
        });
    }

    /** Technician manual clear — wipes entire sync_queue and fail log. */
    public static void clearAllQueueManual(Context ctx, IntConsumer onMainThread) {
        final Context app = ctx.getApplicationContext();
        EXEC.execute(() -> {
            int removed = 0;
            try {
                removed = AppDatabase.getInstance(app).syncJobDao().deleteAll();
                File log = new File(app.getFilesDir(), "sync_fail_log.csv");
                if (log.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    log.delete();
                }
                try {
                    WorkManager.getInstance(app).cancelUniqueWork("sync-drain");
                } catch (Exception ignored) {
                }
                Log.i(TAG, "Manual queue clear removed=" + removed);
            } catch (Exception e) {
                Log.e(TAG, "clearAllQueueManual failed", e);
            }
            int finalRemoved = removed;
            MAIN.post(() -> onMainThread.accept(finalRemoved));
        });
    }

    public static void onStartup(Context ctx) {
        final Context app = ctx.getApplicationContext();
        EXEC.execute(() -> {
            try {
                prune(app);
                if (isOnline(app)) {
                    int pending = AppDatabase.getInstance(app).syncJobDao().count();
                    if (pending > 0) {
                        Log.i(TAG, "Startup: " + pending + " queued sync jobs — scheduling drain");
                        SyncQueueUtil.requestDrain(app, pending > 50);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "onStartup failed", e);
            }
        });
    }
}
