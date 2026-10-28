package com.ayb.busticketpos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.Executors;

/**
 * Runs in the new process after an app update (package replace). Launches the main activity
 * so the app comes back to the foreground after install, without relying on USER_PRESENT
 * (which is often blocked when the app is in background).
 * Also reports INSTALL_SUCCESS to the server so tbl_app_update_reports reflects the actual outcome
 * when InstallResultReceiver only received status=1 (spurious failure) before the replace.
 */
public class PackageReplacedReceiver extends BroadcastReceiver {
    private static final String TAG = "PackageReplacedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            handle(context, intent);
        } catch (Throwable t) {
            AppLog.e(TAG, "onReceive failed", t);
        }
    }

    private void handle(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        Prefs.getAndClearPendingRelaunchAfterUnlock(context); // clear so UnlockReceiver doesn't relaunch again

        // Report INSTALL_SUCCESS to server (we know the package was replaced); corrects spurious INSTALL_FAILED from InstallResultReceiver
        int[] pending = Prefs.getAndClearPendingInstallReportContext(context);
        if (pending != null && pending.length == 3) {
            final Context appContext = context.getApplicationContext();
            final int releaseId = pending[0];
            final int fromV = pending[1];
            final int toV = pending[2];
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    JSONObject r = new JSONObject();
                    r.put("releaseId", releaseId);
                    r.put("fromVersionCode", fromV);
                    r.put("toVersionCode", toV);
                    r.put("stage", "INSTALL");
                    r.put("event", "INSTALL_SUCCESS");
                    SyncQueueUtil.enqueue(appContext, ApiEndpoints.REPORT_URL, r, null);
                    Log.i(TAG, "Reported INSTALL_SUCCESS to server (releaseId=" + releaseId + ")");
                } catch (Exception e) {
                    AppLog.e(TAG, "Failed to report INSTALL_SUCCESS", e);
                }
            });
        }

        try {
            Intent launch = InstallResultReceiver.buildRelaunchIntent(context);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
            Log.i(TAG, "App relaunched after package replace");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to relaunch after update", e);
        }
    }
}
