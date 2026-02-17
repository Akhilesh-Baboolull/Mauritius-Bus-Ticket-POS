package com.ayb.busticketpos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class UnlockReceiver extends BroadcastReceiver {
    /** Delay so we run after the system has finished unlock and shown Settings; then we bring our app on top. */
    private static final long POST_UNLOCK_DELAY_MS = 600;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
            // After an update install, system often shows Settings on top. Run after a short delay
            // so we execute after the unlock transition, then launch our app as "Home" (default launcher).
            if (Prefs.getAndClearPendingRelaunchAfterUnlock(context)) {
                Context app = context.getApplicationContext();
                Handler h = new Handler(Looper.getMainLooper());
                h.postDelayed(() -> {
                    try {
                        Intent home = new Intent(Intent.ACTION_MAIN);
                        home.addCategory(Intent.CATEGORY_HOME);
                        home.setPackage(app.getPackageName());
                        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                        app.startActivity(home);
                        Log.d("UnlockReceiver", "Post-update: launched Home (our app) after unlock");
                    } catch (Exception e) {
                        Log.e("UnlockReceiver", "Failed to bring app to front after unlock", e);
                    }
                }, POST_UNLOCK_DELAY_MS);
            }
            Log.d("UnlockReceiver", "User unlocked device — restarting LocationForegroundService");
            LocationForegroundService.startIfNeeded(context);
        }
    }
}
