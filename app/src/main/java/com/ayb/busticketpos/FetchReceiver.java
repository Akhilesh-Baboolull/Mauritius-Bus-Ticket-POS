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
            AppLog.e("FetchReceiver", "Failed to enqueue daily update", e);
        } finally {
            try {
                App.scheduleDailyFetch(context);
                App.scheduleDailyFetchFallback(context);
            } catch (Exception e) {
                AppLog.e("FetchReceiver", "Reschedule alarm failed", e);
            }
        }
    }
}
