package com.ayb.busticketpos;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.os.BatteryManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UpdateWorker extends Worker {

    private static final String TAG = "UpdateWorker";
    private static final String UNIQUE_SYNC_WORK = "sync-drain"; // from SyncQueueUtil
    private static final int MIN_BATTERY_PERCENT = 40;

    public static final String KEY_MODE = "mode";
    /** Check, download, verify, install (21:00 / 23:59 alarms and Settings "Install now"). */
    public static final String MODE_FULL = "FULL";
    /** Only ask check.php and record the result; never downloads or installs. */
    public static final String MODE_CHECK_ONLY = "CHECK_ONLY";

    public UpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        boolean checkOnly = MODE_CHECK_ONLY.equals(getInputData().getString(KEY_MODE));

        Integer releaseIdForError = null;
        Integer fromVersionForError = null;
        Integer toVersionForError = null;

        try {
            if (!checkOnly) {
                // 1) Battery gate (>= 40%)
                int batt = getBatteryPercent(ctx);
                if (batt >= 0 && batt < MIN_BATTERY_PERCENT) {
                    String msg = "BATTERY_LOW_" + batt;
                    Log.i(TAG, "Battery " + batt + "% < " + MIN_BATTERY_PERCENT + "%, retry later");
                    UpdateCheckLog.log(ctx, "battery_low", batt + "%");
                    Prefs.setLastUpdateResult(ctx, "Skipped: battery " + batt + "% (needs " + MIN_BATTERY_PERCENT + "%)");
                    report(ctx, "CHECK", msg, null, null, null, msg);
                    return Result.retry();
                }

                // 2) Busy gate (avoid fighting sync queue)
                if (isUniqueWorkRunning(ctx, UNIQUE_SYNC_WORK)) {
                    String msg = "BUSY_SYNC";
                    Log.i(TAG, "Sync work busy (" + UNIQUE_SYNC_WORK + "), retry later");
                    UpdateCheckLog.log(ctx, "skipped", "sync_busy");
                    Prefs.setLastUpdateResult(ctx, "Skipped: uploads in progress, will retry");
                    report(ctx, "CHECK", msg, null, null, null, msg);
                    return Result.retry();
                }
            }

            // 3) Provisioning safety net (for now: just retry until provisioning is added)
            String machineIdStr = PrefsSecure.getMachineId(ctx);
            String tenantIdStr = PrefsSecure.getTenantId(ctx);
            String apiKey = PrefsSecure.getApiKey(ctx);

            if (isEmpty(machineIdStr) || isEmpty(tenantIdStr) || isEmpty(apiKey)) {
                String msg = "MISSING_PROVISIONING";
                Log.w(TAG, "Missing provisioning (machine/tenant/api_key). Need self-registration.");
                UpdateCheckLog.log(ctx, "skipped", "missing_provisioning");
                Prefs.setLastUpdateResult(ctx, "Skipped: device not registered");
                report(ctx, "CHECK", msg, null, null, null, msg);
                return Result.retry();
            }

            int deviceId = Integer.parseInt(machineIdStr);

            // 4) Current versionCode (no BuildConfig dependency)
            int currentVersion = getCurrentVersionCode(ctx);
            fromVersionForError = currentVersion;

            // 5) Call check.php
            JSONObject checkBody = new JSONObject();
            checkBody.put("currentVersionCode", currentVersion);

            byte[] bodyBytes = checkBody.toString().getBytes("UTF-8");
            java.util.Map<String, String> headers = UpdateAuth.buildAuthHeaders(deviceId, apiKey, bodyBytes);

            String resp = HttpUtil.postJson(ApiEndpoints.CHECK_URL, checkBody.toString(), headers);
            JSONObject j = new JSONObject(resp);
            long now = System.currentTimeMillis();
            Prefs.setLastUpdateCheckAt(ctx, now);
            if (!checkOnly) {
                Prefs.setLastNightlyUpdateRunAt(ctx, now);
            }

            if (!j.optBoolean("updateAvailable", false)) {
                Log.i(TAG, "No update available (checkOnly=" + checkOnly + ")");
                UpdateCheckLog.log(ctx, "no_update", checkOnly ? "check_only" : null);
                Prefs.clearUpdateAvailable(ctx);
                Prefs.setLastUpdateResult(ctx, "No update available");
                reportCheckNow(ctx, "NO_UPDATE", null, currentVersion, null);
                if (!checkOnly) {
                    runDbSync(ctx);
                }
                return Result.success();
            }

            int releaseId = j.getInt("releaseId");
            int targetVersionCode = j.getInt("versionCode");
            String targetVersionName = j.optString("versionName", String.valueOf(targetVersionCode));

            releaseIdForError = releaseId;
            toVersionForError = targetVersionCode;

            Prefs.setUpdateAvailable(ctx, targetVersionCode, targetVersionName);

            if (checkOnly) {
                Log.i(TAG, "Update available " + targetVersionCode + " (check only; install at nightly run)");
                UpdateCheckLog.log(ctx, "update_available", currentVersion + " -> " + targetVersionCode);
                Prefs.setLastUpdateResult(ctx, "Update available: " + targetVersionName
                        + " (" + targetVersionCode + "), installs at 21:00");
                report(ctx, "UPDATE_AVAILABLE", null, releaseId, currentVersion, targetVersionCode, null);
                return Result.success();
            }

            String expectedSha = j.getString("sha256").toLowerCase(Locale.US);
            String downloadUrl = j.getString("downloadUrl");

            report(ctx, "UPDATE_AVAILABLE", null, releaseId, currentVersion, targetVersionCode, null);

            // 6) Download APK to app-private file (streaming; low RAM)
            File out = new File(ctx.getExternalFilesDir(null), "update.apk");
            safeDelete(out);

            try {
                HttpUtil.downloadToFile(downloadUrl, out);
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                String msg = safeMsg(e);
                UpdateCheckLog.log(ctx, "error", "download_failed: " + (msg != null ? msg : ""));
                Prefs.setLastUpdateResult(ctx, "Download failed, will retry");
                report(ctx, "DOWNLOAD_FAILED", msg, releaseId, currentVersion, targetVersionCode, msg);
                safeDelete(out);
                return Result.retry(); // transient
            }

            // 7) Verify sha256
            String actualSha = UpdateAuth.sha256FileHex(out).toLowerCase(Locale.US);
            if (!expectedSha.equals(actualSha)) {
                String msg = "SHA256_MISMATCH";
                Log.e(TAG, "SHA256 mismatch expected=" + expectedSha + " actual=" + actualSha);
                UpdateCheckLog.log(ctx, "error", "verify_failed");
                Prefs.setLastUpdateResult(ctx, "Update file failed verification");
                report(ctx, "VERIFY_FAILED", msg, releaseId, currentVersion, targetVersionCode, msg);
                safeDelete(out);
                runDbSync(ctx); // no install; run sync now
                return Result.success(); // don't retry bad artifact; wait for next day/release
            }

            // 8) Install (Device Owner silent install). Set flag so relaunched app runs DB sync.
            Prefs.setRunDbSyncOnNextLaunch(ctx, true);
            try {
                Prefs.setLastUpdateResult(ctx, "Installing " + targetVersionName + " (" + targetVersionCode + ")");
                UpdateInstaller.installApk(ctx, out, releaseId, currentVersion, targetVersionCode);
                UpdateCheckLog.log(ctx, "updated", currentVersion + " -> " + targetVersionCode);
            } catch (Exception e) {
                Log.e(TAG, "Install start failed", e);
                String msg = safeMsg(e);
                UpdateCheckLog.log(ctx, "error", "install_failed: " + (msg != null ? msg : ""));
                Prefs.setLastUpdateResult(ctx, "Install failed: " + (msg != null ? msg : "unknown"));
                report(ctx, "INSTALL_FAILED", msg, releaseId, currentVersion, targetVersionCode, msg);
                Prefs.setRunDbSyncOnNextLaunch(ctx, false); // clear in case we set it above
                runDbSync(ctx); // install failed; run sync now
                return Result.success(); // old app remains usable
            }

            // Install result is reported by InstallResultReceiver
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Unexpected failure", e);
            String msg = safeMsg(e);
            UpdateCheckLog.log(ctx, "error", "check_exception: " + (msg != null ? msg : ""));
            Prefs.setLastUpdateResult(ctx, "Check failed: " + (msg != null ? msg : "unknown") + ", will retry");
            report(ctx, "CHECK", "EXCEPTION", releaseIdForError, fromVersionForError, toVersionForError, msg);
            return Result.retry();
        }
    }

    // --- Helpers ---

    private static boolean isUniqueWorkRunning(Context ctx, String uniqueName) {
        try {
            List<WorkInfo> infos = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(uniqueName).get();
            if (infos == null) return false;
            for (WorkInfo wi : infos) {
                WorkInfo.State s = wi.getState();
                if (s == WorkInfo.State.RUNNING || s == WorkInfo.State.ENQUEUED) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Use the same battery source as the UI (sticky ACTION_BATTERY_CHANGED) so the reported
     * value matches what the user sees (e.g. 98%). BatteryManager.getIntProperty can be stale
     * or wrong on some devices when the worker runs.
     */
    private static int getBatteryPercent(Context ctx) {
        try {
            Intent batt = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batt != null) {
                int level = batt.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batt.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return (int) ((level / (float) scale) * 100);
                }
            }
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (cap >= 0 && cap <= 100) return cap;
            }
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    private static int getCurrentVersionCode(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            long vc = pi.getLongVersionCode();
            return (int) vc;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void safeDelete(File f) {
        try { if (f != null && f.exists()) f.delete(); } catch (Exception ignored) {}
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeMsg(Exception e) {
        if (e == null) return null;
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) m = e.getClass().getSimpleName();
        // keep it short so it fits your DB field
        return m.length() > 200 ? m.substring(0, 200) : m;
    }

    private static String inferStage(String event) {
        if (event == null) return "CHECK";
        if (event.contains("DOWNLOAD")) return "DOWNLOAD";
        if (event.contains("VERIFY")) return "VERIFY";
        if (event.contains("INSTALL")) return "INSTALL";
        return "CHECK";
    }

    /** Run DB fetch/sync (update-first flow: after update check when no install, or after failed install). */
    private static void runDbSync(Context ctx) {
        try {
            DatabaseSyncManager.forceSync(ctx, null);
            Log.i(TAG, "DB sync started");
        } catch (Exception e) {
            Log.e(TAG, "DB sync start failed", e);
        }
    }

    /**
     * CHECK-stage audit log for tbl_app_update_reports — POST immediately while online,
     * then queue as fallback if the direct call fails.
     */
    private static void reportCheckNow(Context ctx, String message,
                                       Integer releaseId, Integer fromV, Integer toV) {
        try {
            JSONObject r = buildReportPayload("CHECK", message, releaseId, fromV, toV);
            if (postReportImmediate(ctx, r)) {
                Log.i(TAG, "CHECK report saved on server: " + message);
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "Immediate CHECK report failed, queueing: " + message, e);
        }
        enqueueReport(ctx, "CHECK", message, releaseId, fromV, toV, message);
        SyncQueueUtil.requestDrain(ctx, true);
    }

    /** Non-CHECK events (download/install) — sync queue is fine. */
    private static void report(Context ctx, String event, String message,
                               Integer releaseId, Integer fromV, Integer toV,
                               String lastError) {
        if ("CHECK".equals(event)) {
            reportCheckNow(ctx, message, releaseId, fromV, toV);
            return;
        }
        enqueueReport(ctx, event, message, releaseId, fromV, toV, lastError);
    }

    private static void enqueueReport(Context ctx, String event, String message,
                                      Integer releaseId, Integer fromV, Integer toV,
                                      String lastError) {
        try {
            SyncQueueUtil.enqueue(ctx, ApiEndpoints.REPORT_URL,
                    buildReportPayload(event, message, releaseId, fromV, toV), lastError);
        } catch (Exception e) {
            Log.w(TAG, "enqueueReport failed: " + event, e);
        }
    }

    private static JSONObject buildReportPayload(String event, String message,
                                                 Integer releaseId, Integer fromV, Integer toV)
            throws Exception {
        JSONObject r = new JSONObject();
        r.put("event", event);
        r.put("stage", inferStage(event));
        if (message != null) r.put("message", message);
        if (releaseId != null) r.put("releaseId", releaseId);
        if (fromV != null) r.put("fromVersionCode", fromV);
        if (toV != null) r.put("toVersionCode", toV);
        return r;
    }

    /** @return true if report.php accepted the row */
    private static boolean postReportImmediate(Context ctx, JSONObject payload) throws Exception {
        String machineIdStr = PrefsSecure.getMachineId(ctx);
        String apiKey = PrefsSecure.getApiKey(ctx);
        if (isEmpty(machineIdStr) || isEmpty(apiKey)) return false;

        int deviceId = Integer.parseInt(machineIdStr.trim());
        String body = payload.toString();
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = UpdateAuth.buildAuthHeaders(deviceId, apiKey, bodyBytes);
        String respStr = HttpUtil.postJson(ApiEndpoints.REPORT_URL, body, headers);
        JSONObject resp = HttpUtil.parseJsonObjectLenient(respStr);
        return resp.optBoolean("ok", false)
                || "success".equalsIgnoreCase(resp.optString("status", ""));
    }
}
