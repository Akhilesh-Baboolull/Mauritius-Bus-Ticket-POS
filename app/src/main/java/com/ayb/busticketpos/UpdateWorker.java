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
import java.util.List;
import java.util.Locale;

public class UpdateWorker extends Worker {

    private static final String TAG = "UpdateWorker";
    private static final String UNIQUE_SYNC_WORK = "sync-drain"; // from SyncQueueUtil
    private static final int MIN_BATTERY_PERCENT = 40;

    public UpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();

        Integer releaseIdForError = null;
        Integer fromVersionForError = null;
        Integer toVersionForError = null;

        try {
            // 1) Battery gate (>= 40%)
            int batt = getBatteryPercent(ctx);
            if (batt >= 0 && batt < MIN_BATTERY_PERCENT) {
                String msg = "BATTERY_LOW_" + batt;
                Log.i(TAG, "Battery " + batt + "% < " + MIN_BATTERY_PERCENT + "%, retry later");
                report(ctx, "CHECK", msg, null, null, null, msg);
                return Result.retry();
            }

            // 2) Busy gate (avoid fighting sync queue)
            if (isUniqueWorkRunning(ctx, UNIQUE_SYNC_WORK)) {
                String msg = "BUSY_SYNC";
                Log.i(TAG, "Sync work busy (" + UNIQUE_SYNC_WORK + "), retry later");
                report(ctx, "CHECK", msg, null, null, null, msg);
                return Result.retry();
            }

            // 3) Provisioning safety net (for now: just retry until provisioning is added)
            String machineIdStr = PrefsSecure.getMachineId(ctx);
            String tenantIdStr = PrefsSecure.getTenantId(ctx);
            String apiKey = PrefsSecure.getApiKey(ctx);

            if (isEmpty(machineIdStr) || isEmpty(tenantIdStr) || isEmpty(apiKey)) {
                String msg = "MISSING_PROVISIONING";
                Log.w(TAG, "Missing provisioning (machine/tenant/api_key). Need self-registration.");
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

            String resp = UpdateHttp.postJson(UpdateConfig.CHECK_URL, checkBody.toString(), headers);
            JSONObject j = new JSONObject(resp);

            if (!j.optBoolean("updateAvailable", false)) {
                Log.i(TAG, "No update available");
                // Run DB sync now (update-first flow: sync after update check when no update)
                runDbSync(ctx);
                return Result.success();
            }

            int releaseId = j.getInt("releaseId");
            int targetVersionCode = j.getInt("versionCode");
            String expectedSha = j.getString("sha256").toLowerCase(Locale.US);
            String downloadUrl = j.getString("downloadUrl");

            releaseIdForError = releaseId;
            toVersionForError = targetVersionCode;

            report(ctx, "UPDATE_AVAILABLE", null, releaseId, currentVersion, targetVersionCode, null);

            // 6) Download APK to app-private file (streaming; low RAM)
            File out = new File(ctx.getExternalFilesDir(null), "update.apk");
            safeDelete(out);

            try {
                UpdateHttp.downloadToFile(downloadUrl, out);
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                String msg = safeMsg(e);
                report(ctx, "DOWNLOAD_FAILED", msg, releaseId, currentVersion, targetVersionCode, msg);
                safeDelete(out);
                return Result.retry(); // transient
            }

            // 7) Verify sha256
            String actualSha = UpdateAuth.sha256FileHex(out).toLowerCase(Locale.US);
            if (!expectedSha.equals(actualSha)) {
                String msg = "SHA256_MISMATCH";
                Log.e(TAG, "SHA256 mismatch expected=" + expectedSha + " actual=" + actualSha);
                report(ctx, "VERIFY_FAILED", msg, releaseId, currentVersion, targetVersionCode, msg);
                safeDelete(out);
                runDbSync(ctx); // no install; run sync now
                return Result.success(); // don't retry bad artifact; wait for next day/release
            }

            // 8) Install (Device Owner silent install). Set flag so relaunched app runs DB sync.
            Prefs.setRunDbSyncOnNextLaunch(ctx, true);
            try {
                UpdateInstaller.installApk(ctx, out, releaseId, currentVersion, targetVersionCode);
            } catch (Exception e) {
                Log.e(TAG, "Install start failed", e);
                String msg = safeMsg(e);
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
            PrefsCache.saveLastFetchTime(ctx, 0L);
            DatabaseSyncManager.startSync(ctx, AppDatabase.getInstance(ctx));
            Log.i(TAG, "DB sync started");
        } catch (Exception e) {
            Log.e(TAG, "DB sync start failed", e);
        }
    }

    /**
     * Reporting via your SyncQueueUtil (4 args).
     * lastError is what your SyncJob stores; we put the real error there.
     */
    private static void report(Context ctx, String event, String message,
                               Integer releaseId, Integer fromV, Integer toV,
                               String lastError) {
        try {
            JSONObject r = new JSONObject();
            r.put("event", event);
            r.put("stage", inferStage(event));
            if (message != null) r.put("message", message);
            if (releaseId != null) r.put("releaseId", releaseId);
            if (fromV != null) r.put("fromVersionCode", fromV);
            if (toV != null) r.put("toVersionCode", toV);

            SyncQueueUtil.enqueue(ctx, UpdateConfig.REPORT_URL, r, lastError);
        } catch (Exception ignored) {}
    }
}
