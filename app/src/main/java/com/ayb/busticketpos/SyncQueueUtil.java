// SyncQueueUtil.java
package com.ayb.busticketpos;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONObject;

public final class SyncQueueUtil {
    private SyncQueueUtil(){}

    public static long enqueue(Context ctx, String endpoint, JSONObject payload, String lastError) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        SyncJob job = new SyncJob();
        job.endpoint = endpoint;
        job.payloadJson = payload.toString();
        job.retryCount = 0;
        job.createdAt = System.currentTimeMillis();
        job.lastError = lastError;
        long id = db.syncJobDao().insert(job);

        // schedule a worker in ~20s, network required
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setInitialDelay(20, java.util.concurrent.TimeUnit.SECONDS)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        // Use a unique chain so multiple calls coalesce
        WorkManager.getInstance(ctx).enqueueUniqueWork(
                "sync-drain",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                req
        );
        return id;
    }
}
