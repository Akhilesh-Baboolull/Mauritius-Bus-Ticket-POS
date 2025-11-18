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

        LocationForegroundService.startIfNeeded(ctx);

        InternetEnforcerReceiver.enforce(ctx);
        InternetEnforcerScheduler.schedule(ctx);

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {

            Log.d("BootReceiver", "Device rebooted — checking if app already running...");

            Prefs.setBootReady(ctx, false); // reset on each reboot

            // 🧩 Check if Menu activity is already running as the launcher
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                if (!tasks.isEmpty()) {
                    ComponentName topActivity = tasks.get(0).topActivity;
                    if (topActivity != null &&
                            topActivity.getPackageName().equals(ctx.getPackageName())) {
                        Log.d("BootReceiver", "App already running — skip launching Menu");
                        return; // 🚫 skip duplicate launch
                    }
                }
            }

            // Only launch if not already running
            Log.d("BootReceiver", "App not running — launching Menu...");
            Intent launchIntent = new Intent(ctx, Menu.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(launchIntent);
        }
    }

}
