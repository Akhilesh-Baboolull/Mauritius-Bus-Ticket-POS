// SyncWorker.java
package com.ayb.busticketpos;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SyncQueueMaintenance.prune(ctx);

        AppDatabase db = AppDatabase.getInstance(ctx);
        SyncJobDao dao = db.syncJobDao();

        int pending = dao.count();
        if (pending == 0) return Result.success();

        int batchSize = pending > 80 ? 80 : Math.min(50, pending);
        List<SyncJob> batch = dao.fetchBatchPrioritized(
                ApiEndpoints.REPORT_URL,
                ApiEndpoints.CREATE_DAY,
                ApiEndpoints.START_TRIP,
                ApiEndpoints.RECORD_TICKET,
                ApiEndpoints.END_TRIP,
                ApiEndpoints.END_DAY,
                ApiEndpoints.BLANK_TICKET,
                batchSize
        );
        if (batch.isEmpty()) return Result.success();

        Log.i(TAG, "Draining batch=" + batch.size() + " pending=" + pending);

        boolean anyFailed = false;

        for (SyncJob job : batch) {
            if (ApiEndpoints.POSITIONING.equals(job.endpoint)
                    && job.retryCount >= SyncQueueMaintenance.MAX_POSITIONING_RETRIES) {
                dao.deleteById(job.id);
                continue;
            }
            if (!ApiEndpoints.POSITIONING.equals(job.endpoint)
                    && job.retryCount >= SyncQueueMaintenance.MAX_BUSINESS_RETRIES) {
                Log.w(TAG, "Giving up after max retries id=" + job.id + " " + job.endpoint);
                dao.deleteById(job.id);
                continue;
            }

            try {
                String respStr;
                if (ApiEndpoints.REPORT_URL.equals(job.endpoint)) {
                    String machineIdStr = PrefsSecure.getMachineId(ctx);
                    String apiKey = PrefsSecure.getApiKey(ctx);
                    if (machineIdStr == null || machineIdStr.trim().isEmpty()
                            || apiKey == null || apiKey.trim().isEmpty()) {
                        anyFailed = true;
                        dao.markFailed(job.id, "Missing provisioning for report");
                        continue;
                    }
                    int deviceId = Integer.parseInt(machineIdStr.trim());
                    byte[] bodyBytes = job.payloadJson.getBytes(StandardCharsets.UTF_8);
                    Map<String, String> headers = UpdateAuth.buildAuthHeaders(deviceId, apiKey, bodyBytes);
                    respStr = HttpUtil.postJson(job.endpoint, job.payloadJson, headers);
                } else {
                    respStr = HttpUtil.postJson(job.endpoint, job.payloadJson);
                }

                JSONObject resp;
                try {
                    resp = HttpUtil.parseJsonObjectLenient(respStr);
                } catch (JSONException je) {
                    Log.e(TAG, job.endpoint + " parse failed: " + je.getMessage());
                    anyFailed = true;
                    dao.markFailed(job.id, je.getMessage());
                    continue;
                }
                boolean success = "success".equalsIgnoreCase(resp.optString("status", ""))
                        || resp.optBoolean("ok", false);

                if (success) {
                    if (ApiEndpoints.CREATE_DAY.equals(job.endpoint)) {
                        int serverDayId = resp.optInt("id", 0);
                        if (serverDayId > 0) {
                            Prefs.saveCurrentServerDayID(ctx, serverDayId);
                        }
                    } else if (ApiEndpoints.START_TRIP.equals(job.endpoint)) {
                        int tripId = resp.optInt("trip_id", 0);
                        if (tripId > 0) {
                            Prefs.saveCurrentServerTripID(ctx, tripId);
                        }
                    }
                    dao.deleteById(job.id);
                    Prefs.setLastUploadSuccessAt(ctx, System.currentTimeMillis());
                } else {
                    anyFailed = true;
                    dao.markFailed(job.id, resp.optString("message", "server fail"));
                }
            } catch (Exception e) {
                anyFailed = true;
                dao.markFailed(job.id, e.getMessage());
            }
        }

        int remaining = dao.count();
        if (remaining > 0) {
            int delaySec = remaining > 100 ? 2 : (remaining > 30 ? 5 : 10);
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setInitialDelay(delaySec, TimeUnit.SECONDS)
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build();
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                    "sync-drain",
                    ExistingWorkPolicy.KEEP,
                    req
            );
            Log.d(TAG, "Scheduled next drain in " + delaySec + "s remaining=" + remaining);
        }

        return anyFailed ? Result.retry() : Result.success();
    }
}
