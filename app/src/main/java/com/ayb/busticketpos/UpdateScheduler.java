package com.ayb.busticketpos;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class UpdateScheduler {
    private static final String TAG = "UpdateScheduler";
    public static final String UNIQUE_FULL = "app-update";
    public static final String UNIQUE_CHECK_ONLY = "app-update-check";

    private UpdateScheduler() {}

    /**
     * Enqueue a one-time update check (called from FetchReceiver when the daily alarm fires).
     * UpdateWorker runs after initial delay; on success/failure it may run DB sync or report to server.
     */
    public static void enqueueDailyUpdate(Context ctx) {
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(UpdateWorker.class)
                    .setInputData(new Data.Builder()
                            .putString(UpdateWorker.KEY_MODE, UpdateWorker.MODE_FULL)
                            .build())
                    .setConstraints(constraints)
                    // Short delay so update check runs soon after alarm; DB sync runs after update (or on next launch).
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .build();

            // REPLACE so each day's 21:00 alarm schedules a fresh check (KEEP ignored stale work).
            WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniqueWork(
                    UNIQUE_FULL,
                    ExistingWorkPolicy.REPLACE,
                    req
            );
            android.util.Log.i(TAG, "Daily update work enqueued (unique name: app-update, initial delay 1 min)");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to enqueue daily update work", e);
        }
    }

    /**
     * Ask the server whether a newer release exists without downloading or installing.
     * Separate unique name so it never replaces a pending nightly FULL run.
     */
    public static void enqueueCheckOnly(Context ctx, boolean delayed) {
        try {
            OneTimeWorkRequest.Builder b = new OneTimeWorkRequest.Builder(UpdateWorker.class)
                    .setInputData(new Data.Builder()
                            .putString(UpdateWorker.KEY_MODE, UpdateWorker.MODE_CHECK_ONLY)
                            .build())
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES);
            if (delayed) {
                b.setInitialDelay(1, TimeUnit.MINUTES);
            }
            WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniqueWork(
                    UNIQUE_CHECK_ONLY,
                    ExistingWorkPolicy.REPLACE,
                    b.build()
            );
            android.util.Log.i(TAG, "Check-only update work enqueued (delayed=" + delayed + ")");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to enqueue check-only update work", e);
        }
    }

    /** Settings "Install now": same FULL flow as the nightly run, without the 1-minute delay. */
    public static void enqueueInstallNow(Context ctx) {
        try {
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(UpdateWorker.class)
                    .setInputData(new Data.Builder()
                            .putString(UpdateWorker.KEY_MODE, UpdateWorker.MODE_FULL)
                            .build())
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .build();
            WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniqueWork(
                    UNIQUE_FULL,
                    ExistingWorkPolicy.REPLACE,
                    req
            );
            android.util.Log.i(TAG, "Install-now update work enqueued");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to enqueue install-now work", e);
        }
    }
}
