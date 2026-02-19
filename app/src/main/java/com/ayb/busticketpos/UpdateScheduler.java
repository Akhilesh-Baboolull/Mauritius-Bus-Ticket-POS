package com.ayb.busticketpos;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class UpdateScheduler {
    private static final String TAG = "UpdateScheduler";

    private UpdateScheduler() {}

    /**
     * Enqueue a one-time update check (called from FetchReceiver when the daily alarm fires).
     * UpdateWorker runs after initial delay; on success/failure it may run DB sync or report to server.
     */
    public static void enqueueDailyUpdate(Context ctx) {
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build();

            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(UpdateWorker.class)
                    .setConstraints(constraints)
                    // Short delay so update check runs soon after alarm; DB sync runs after update (or on next launch).
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .build();

            WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniqueWork(
                    "app-update",
                    ExistingWorkPolicy.KEEP,
                    req
            );
            android.util.Log.i(TAG, "Daily update work enqueued (unique name: app-update, initial delay 1 min)");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to enqueue daily update work", e);
        }
    }
}
