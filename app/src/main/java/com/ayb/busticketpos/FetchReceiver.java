package com.ayb.busticketpos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class FetchReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("FetchReceiver", "Daily fetch triggered — enqueue update work and reschedule alarm");
        try {
            // Update first (check → install → relaunch); DB sync runs after update flow (see UpdateWorker / App).
            UpdateScheduler.enqueueDailyUpdate(context);
            Log.d("FetchReceiver", "Update work enqueued successfully");
        } catch (Exception e) {
            Log.e("FetchReceiver", "Failed to enqueue daily update", e);
        } finally {
            // Always reschedule next day so the alarm is never lost (exact alarm is one-shot).
            Log.d("FetchReceiver", "Rescheduling next daily fetch alarm");
            App.scheduleDailyFetch(context);
        }
    }
}
