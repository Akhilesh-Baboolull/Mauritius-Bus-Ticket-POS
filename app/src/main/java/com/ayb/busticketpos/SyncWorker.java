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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(ctx);
        SyncJobDao dao = db.syncJobDao();

        List<SyncJob> batch = dao.fetchBatch(25);
        if (batch.isEmpty()) return Result.success();

        boolean anyFailed = false;

        for (SyncJob job : batch) {
            try {
                String respStr;
                if (UpdateConfig.REPORT_URL.equals(job.endpoint)) {
                    // Update report endpoint requires X-Device-Id / X-Signature auth
                    String machineIdStr = PrefsSecure.getMachineId(ctx);
                    String apiKey = PrefsSecure.getApiKey(ctx);
                    if (machineIdStr == null || machineIdStr.trim().isEmpty() || apiKey == null || apiKey.trim().isEmpty()) {
                        anyFailed = true;
                        dao.markFailed(job.id, "Missing provisioning for report");
                        continue;
                    }
                    int deviceId = Integer.parseInt(machineIdStr.trim());
                    byte[] bodyBytes = job.payloadJson.getBytes(StandardCharsets.UTF_8);
                    Map<String, String> headers = UpdateAuth.buildAuthHeaders(deviceId, apiKey, bodyBytes);
                    respStr = UpdateHttp.postJson(job.endpoint, job.payloadJson, headers);
                } else {
                    respStr = HttpUtil.postJson(job.endpoint, job.payloadJson);
                }

                JSONObject resp = new JSONObject(respStr);
                // Accept both legacy "status":"success" and report.php "ok":true
                boolean success = "success".equalsIgnoreCase(resp.optString("status", ""))
                        || resp.optBoolean("ok", false);

                if (success) {
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
