package com.ayb.busticketpos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Runs in the new process after an app update (package replace). Launches the main activity
 * so the app comes back to the foreground after install, without relying on USER_PRESENT
 * (which is often blocked when the app is in background).
 */
public class PackageReplacedReceiver extends BroadcastReceiver {
    private static final String TAG = "PackageReplacedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        Prefs.getAndClearPendingRelaunchAfterUnlock(context); // clear so UnlockReceiver doesn't relaunch again
        try {
            Intent launch = InstallResultReceiver.buildRelaunchIntent(context);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
            Log.i(TAG, "App relaunched after package replace");
        } catch (Exception e) {
            Log.e(TAG, "Failed to relaunch after update", e);
        }
    }
}
