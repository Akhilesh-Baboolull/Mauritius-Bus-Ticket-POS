package com.ayb.busticketpos;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Shared kiosk (lock task) logic. Use from LockFirstActivity (lock first on startup/unlock)
 * and from Menu (full kiosk + status bar overlay, and re-apply on resume).
 */
public final class KioskHelper {
    private KioskHelper() {}

    /**
     * If this app is device owner: set lock task packages, grant overlay/location permissions,
     * then start lock task on the given activity. Does not add the status bar overlay
     * (Menu does that). Call this as early as possible (e.g. from LockFirstActivity.onCreate).
     */
    public static void applyLockTaskOnly(AppCompatActivity activity) {
        Context context = activity.getApplicationContext();
        DevicePolicyManager dpm =
                (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, MyDeviceAdminReceiver.class);

        if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
            Log.w("KioskHelper", "App is not Device Owner; cannot start Lock Task");
            return;
        }
        try {
            dpm.setLockTaskPackages(admin, new String[]{context.getPackageName()});

            dpm.setPermissionGrantState(admin,
                    context.getPackageName(),
                    "android.permission.SYSTEM_ALERT_WINDOW",
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);

            try {
                dpm.setPermissionGrantState(admin, context.getPackageName(),
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                dpm.setPermissionGrantState(admin, context.getPackageName(),
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                dpm.setPermissionGrantState(admin, context.getPackageName(),
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    dpm.setPermissionGrantState(admin, context.getPackageName(),
                            "android.permission.FOREGROUND_SERVICE_LOCATION",
                            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                }
            } catch (SecurityException se) {
                Log.w("KioskHelper", "Failed to grant location permissions", se);
            }

            try {
                dpm.setStatusBarDisabled(admin, false);
            } catch (SecurityException se) {
                Log.w("KioskHelper", "Could not set status bar", se);
            }

            activity.startLockTask();
            Log.d("KioskHelper", "Lock Task started");

            dpm.setAutoTimeRequired(admin, true);
            dpm.setLockTaskPackages(admin, new String[]{context.getPackageName()});
            dpm.setApplicationHidden(admin, context.getPackageName(), false);
            dpm.setApplicationRestrictions(admin, context.getPackageName(), new Bundle());
        } catch (Exception e) {
            Log.e("KioskHelper", "Failed to start Lock Task", e);
        }
    }
}
