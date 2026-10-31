// SyncQueueUtil.java
package com.ayb.busticketpos;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public final class SyncQueueUtil {
    private static final String TAG = "SyncQueueUtil";
    private static final String UNIQUE_DRAIN = "sync-drain";

    private SyncQueueUtil() {}

    public static long enqueue(Context ctx, String endpoint, JSONObject payload, String lastError) {
        return enqueue(ctx, endpoint, payload, lastError, true);
    }

    /**
     * @param scheduleDrain when false, only persists (used for positioning coalesce — drain scheduled separately).
     */
    public static long enqueue(Context ctx, String endpoint, JSONObject payload, String lastError,
                               boolean scheduleDrain) {
        Context app = ctx.getApplicationContext();

        // Positioning: replace pending rows with one latest ping instead of thousands while offline.
        if (ApiEndpoints.POSITIONING.equals(endpoint)) {
            try {
                AppDatabase.getInstance(app).syncJobDao().deleteAllByEndpoint(ApiEndpoints.POSITIONING);
            } catch (Exception e) {
                AppLog.w(TAG, "Could not clear old positioning queue rows", e);
            }
        }

        final String endpointFinal = endpoint;
        final String payloadStr = payload.toString();
        final String lastErrorFinal = lastError;
        AppExecutors.runBg(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(app);
                SyncJob job = new SyncJob();
                job.endpoint = endpointFinal;
                job.payloadJson = payloadStr;
                job.retryCount = 0;
                job.createdAt = System.currentTimeMillis();
                job.lastError = lastErrorFinal;
                db.syncJobDao().insert(job);
                final boolean urgent = scheduleDrain && !ApiEndpoints.POSITIONING.equals(endpointFinal);
                SyncQueueMaintenance.runAfterEnqueue(app, scheduleDrain, urgent);
            } catch (Exception e) {
                AppLog.e(TAG, "enqueue failed", e);
            }
        });
        return -1L;
    }

    /** Schedule (or coalesce) background drain. Uses KEEP so in-flight worker is not cancelled. */
    public static void requestDrain(Context ctx, boolean fast) {
        try {
            int delaySec = fast ? 3 : 12;
            int pending = AppDatabase.getInstance(ctx).syncJobDao().count();
            if (pending > 100) delaySec = 2;

            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setInitialDelay(delaySec, TimeUnit.SECONDS)
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build();
            WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniqueWork(
                    UNIQUE_DRAIN,
                    ExistingWorkPolicy.KEEP,
                    req
            );
            Log.d(TAG, "Drain scheduled in " + delaySec + "s pending=" + pending);
        } catch (Exception e) {
            AppLog.e(TAG, "requestDrain failed", e);
        }
    }

    /** Called by SyncWorker at the end of a run: queue the next run after this one finishes. */
    static void scheduleFollowUp(Context ctx, long delayMs) {
        try {
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setInitialDelay(Math.max(0L, delayMs), TimeUnit.MILLISECONDS)
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build();
            WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniqueWork(
                    UNIQUE_DRAIN, ExistingWorkPolicy.APPEND_OR_REPLACE, req);
        } catch (Exception e) {
            AppLog.e(TAG, "scheduleFollowUp failed", e);
        }
    }

    /** Settings "Send pending data now": skip any waiting delay and send immediately. */
    public static void requestDrainNow(Context ctx) {
        try {
            Context app = ctx.getApplicationContext();
            SyncDispatcher.resetBackoff(app);
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build();
            WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE_DRAIN, ExistingWorkPolicy.REPLACE, req);
        } catch (Exception e) {
            AppLog.e(TAG, "requestDrainNow failed", e);
        }
    }
}
