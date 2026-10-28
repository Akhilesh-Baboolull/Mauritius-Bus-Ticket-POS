package com.ayb.busticketpos;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            try { App.scheduleDailyFetch(ctx); } catch (Exception e) { AppLog.w("BootReceiver", "scheduleDailyFetch failed", e); }
            try { App.scheduleDailyFetchFallback(ctx); } catch (Exception e) { AppLog.w("BootReceiver", "scheduleDailyFetchFallback failed", e); }
            try { LocationForegroundService.startIfNeeded(ctx); } catch (Exception e) { AppLog.w("BootReceiver", "LocationForegroundService start failed", e); }
            try { InternetEnforcerReceiver.enforce(ctx); } catch (Exception e) { AppLog.w("BootReceiver", "InternetEnforcerReceiver failed", e); }
            try { InternetEnforcerScheduler.schedule(ctx); } catch (Exception e) { AppLog.w("BootReceiver", "InternetEnforcerScheduler failed", e); }
        } catch (Exception e) {
            AppLog.e("BootReceiver", "onReceive failed", e);
            return;
        }

        if (intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        try {
            Log.d("BootReceiver", "Device rebooted — checking if app already running...");
            Prefs.setBootReady(ctx, false);
            Prefs.setLockFirstShownThisBoot(ctx, false);

            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                if (!tasks.isEmpty()) {
                    ComponentName topActivity = tasks.get(0).topActivity;
                    if (topActivity != null && topActivity.getPackageName().equals(ctx.getPackageName())) {
                        Log.d("BootReceiver", "App already running — skip launching Menu");
                        return;
                    }
                }
            }

            Log.d("BootReceiver", "App not running — launching LockFirstActivity...");
            Intent launchIntent = new Intent(ctx, LockFirstActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(launchIntent);
        } catch (Exception e) {
            AppLog.e("BootReceiver", "Boot launch failed", e);
        }
    }

}
