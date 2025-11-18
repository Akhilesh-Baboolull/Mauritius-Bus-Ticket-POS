// SyncWorker.java
package com.ayb.busticketpos;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.util.List;

public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        SyncJobDao dao = db.syncJobDao();

        List<SyncJob> batch = dao.fetchBatch(25);
        if (batch.isEmpty()) return Result.success();

        boolean anyFailed = false;

        for (SyncJob job : batch) {
            try {
                String respStr = HttpUtil.postJson(job.endpoint, job.payloadJson);
                JSONObject resp = new JSONObject(respStr);
                String status = resp.optString("status", "fail");

                if ("success".equalsIgnoreCase(status)) {
                    // success → delete job
                    dao.deleteById(job.id);
                } else {
                    anyFailed = true;
                    dao.markFailed(job.id, resp.optString("message", "server fail"));
                }
            } catch (Exception e) {
                anyFailed = true;
                dao.markFailed(job.id, e.getMessage());
            }
        }

        // If items remain, schedule another attempt
        if (dao.count() > 0) {
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setInitialDelay(20, java.util.concurrent.TimeUnit.SECONDS)
                    .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build();
            WorkManager.getInstance(getApplicationContext()).enqueue(req);
        }

        return anyFailed ? Result.retry() : Result.success();
    }
}
