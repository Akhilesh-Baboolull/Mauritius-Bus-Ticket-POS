// SyncWorker.java
package com.ayb.busticketpos;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Drains sync_queue.
 *  1) Business uploads (day / trip / ticket ...) via {@link SyncDispatcher}: strict order, never dropped.
 *  2) Everything else (update reports, positioning, audit events): best effort, as before.
 * The next run is scheduled here (APPEND_OR_REPLACE) instead of Result.retry(), so WorkManager's
 * exponential backoff cannot push the next attempt hours away.
 */
public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    private static final int MAX_BUSINESS_JOBS_PER_RUN = 150;
    private static final int OTHER_BATCH = 50;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        long nextRunMs = -1L;
        try {
            SyncQueueMaintenance.prune(ctx);

            SyncDispatcher.Outcome biz = SyncDispatcher.drain(ctx, MAX_BUSINESS_JOBS_PER_RUN, this::isStopped);
            if (biz.state != SyncDispatcher.State.EMPTY) {
                nextRunMs = biz.retryInMs;
            }

            if (drainOthers(ctx)) {
                nextRunMs = nextRunMs < 0 ? 10_000L : Math.min(nextRunMs, 10_000L);
            }

            // A job queued while this run was finishing: its own drain request was ignored (KEEP).
            if (nextRunMs < 0 && AppDatabase.getInstance(ctx).syncJobDao()
                    .countIn(SyncDispatcher.BUSINESS_ENDPOINTS) > 0) {
                nextRunMs = 2_000L;
            }
        } catch (Throwable t) {
            AppLog.e(TAG, "Sync run failed", t);
            nextRunMs = 30_000L;
        }

        if (nextRunMs >= 0) {
            SyncQueueUtil.scheduleFollowUp(ctx, nextRunMs);
            Log.d(TAG, "Next sync run in " + (nextRunMs / 1000) + "s");
        }
        return Result.success();
    }

    /** Non-business jobs. @return true if some are still waiting. */
    private boolean drainOthers(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        SyncJobDao dao = db.syncJobDao();

        List<SyncJob> batch = dao.fetchBatchExcluding(
                SyncDispatcher.BUSINESS_ENDPOINTS, ApiEndpoints.REPORT_URL, OTHER_BATCH);
        if (batch.isEmpty()) return false;

        for (SyncJob job : batch) {
            if (isStopped()) break;
            if (ApiEndpoints.POSITIONING.equals(job.endpoint)
                    && job.retryCount >= SyncQueueMaintenance.MAX_POSITIONING_RETRIES) {
                dao.deleteById(job.id);
                continue;
            }
            if (!ApiEndpoints.POSITIONING.equals(job.endpoint)
                    && job.retryCount >= SyncQueueMaintenance.MAX_OTHER_RETRIES) {
                AppLog.w(TAG, "Giving up after max retries id=" + job.id + " " + job.endpoint);
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
                    AppLog.e(TAG, job.endpoint + " parse failed: " + je.getMessage());
                    dao.markFailed(job.id, je.getMessage());
                    continue;
                }
                boolean success = "success".equalsIgnoreCase(resp.optString("status", ""))
                        || resp.optBoolean("ok", false);

                if (success) {
                    dao.deleteById(job.id);
                    Prefs.setLastUploadSuccessAt(ctx, System.currentTimeMillis());
                } else {
                    dao.markFailed(job.id, resp.optString("message", "server fail"));
                }
            } catch (Exception e) {
                dao.markFailed(job.id, e.getMessage());
            }
        }

        return dao.count() - dao.countIn(SyncDispatcher.BUSINESS_ENDPOINTS) > 0;
    }
}
