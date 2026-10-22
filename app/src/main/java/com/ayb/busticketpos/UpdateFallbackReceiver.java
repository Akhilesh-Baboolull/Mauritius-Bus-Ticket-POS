package com.ayb.busticketpos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;

/**
 * Fires at 23:59. If no nightly (FULL) update run has reached the server today, enqueues one so
 * the install window still happens when the 21:00 alarm didn't fire.
 */
public class UpdateFallbackReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Context ctx = context.getApplicationContext();
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfToday = cal.getTimeInMillis();
            // Daytime check-only runs also update lastUpdateCheckAt; only a FULL run counts here.
            long lastNightlyRun = Prefs.getLastNightlyUpdateRunAt(ctx);

            if (lastNightlyRun < startOfToday) {
                Log.i("UpdateFallback", "No nightly update run today — enqueuing now (23:59 fallback)");
                try {
                    UpdateScheduler.enqueueDailyUpdate(ctx);
                } catch (Exception e) {
                    Log.e("UpdateFallback", "Failed to enqueue daily update", e);
                }
            }
            try {
                App.scheduleDailyFetchFallback(ctx);
            } catch (Exception e) {
                Log.e("UpdateFallback", "Reschedule fallback alarm failed", e);
            }
        } catch (Exception e) {
            Log.e("UpdateFallback", "onReceive failed", e);
        }
    }
}
