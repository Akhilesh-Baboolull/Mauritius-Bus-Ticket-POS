package com.ayb.busticketpos;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Shared kiosk (lock task) logic. Use from LockFirstActivity (lock first on startup/unlock)
 * and from Menu (full kiosk + status bar overlay, and re-apply on resume).
 */
public final class KioskHelper {
    private KioskHelper() {}

    /** One app-wide overlay so any screen (Menu or Settings) can add or remove it. */
    private static WindowManager blockerWindowManager;
    private static View blockerView;
    private static boolean overlayUnavailableLogged = false;

    /** Transparent 50dp overlay at the top that swallows touches so the status bar can't be pulled down. */
    @SuppressLint("ClickableViewAccessibility")
    public static void addStatusBarBlocker(Context ctx) {
        try {
            if (blockerView != null) return;
            Context app = ctx.getApplicationContext();
            // Android Go never grants "Display over other apps"; lock task already blocks the pull-down.
            if (!android.provider.Settings.canDrawOverlays(app)) {
                if (!overlayUnavailableLogged) {
                    overlayUnavailableLogged = true;
                    Log.i("KioskMode", "Overlay permission not available; relying on lock task to block swipe-down");
                }
                return;
            }
            blockerWindowManager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);

            int height = (int) (50 * app.getResources().getDisplayMetrics().density);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSPARENT);
            params.gravity = Gravity.TOP;

            View v = new View(app);
            v.setBackgroundColor(Color.TRANSPARENT);
            v.setOnTouchListener((view, event) -> true);

            blockerWindowManager.addView(v, params);
            blockerView = v;
            Log.d("KioskMode", "Status bar overlay added");
        } catch (Exception e) {
            AppLog.e("KioskMode", "Failed to block swipe-down", e);
        }
    }

    public static void removeStatusBarBlocker() {
        try {
            if (blockerWindowManager != null && blockerView != null) {
                blockerWindowManager.removeView(blockerView);
                Log.d("KioskMode", "Status bar overlay removed");
            }
        } catch (Exception e) {
            AppLog.e("KioskMode", "Failed to remove status bar blocker", e);
        } finally {
            blockerView = null;
        }
    }

    /**
     * Admin mode: stop lock task, remove the status bar blocker, lift network restrictions
     * and open system Settings. Menu re-applies kiosk on its next resume.
     */
    public static void enterAdminMode(AppCompatActivity activity) {
        try {
            activity.stopLockTask();
            Log.d("AdminAccess", "Kiosk mode disabled");

            removeStatusBarBlocker();

            DevicePolicyManager dpm =
                    (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(activity, MyDeviceAdminReceiver.class);
            if (dpm != null && dpm.isDeviceOwnerApp(activity.getPackageName())) {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_AIRPLANE_MODE);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_WIFI);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
                Log.d("AdminAccess", "Restrictions cleared");
            }

            Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            Log.d("AdminAccess", "Opened Settings for admin");
        } catch (Exception e) {
            AppLog.e("AdminAccess", "Failed to enter admin mode", e);
        }
    }

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
            AppLog.w("KioskHelper", "App is not Device Owner; cannot start Lock Task");
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
                AppLog.w("KioskHelper", "Failed to grant location permissions", se);
            }

            try {
                dpm.setStatusBarDisabled(admin, false);
            } catch (SecurityException se) {
                AppLog.w("KioskHelper", "Could not set status bar", se);
            }

            activity.startLockTask();
            Log.d("KioskHelper", "Lock Task started");

            dpm.setAutoTimeRequired(admin, true);
            dpm.setLockTaskPackages(admin, new String[]{context.getPackageName()});
            dpm.setApplicationHidden(admin, context.getPackageName(), false);
            dpm.setApplicationRestrictions(admin, context.getPackageName(), new Bundle());
        } catch (Exception e) {
            AppLog.e("KioskHelper", "Failed to start Lock Task", e);
        }
    }
}
