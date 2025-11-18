package com.ayb.busticketpos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class FetchReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("FetchReceiver", "Daily fetch triggered");
        try {
            AppDatabase.forceRefresh(context);
        } catch (Exception e) {
            Log.e("FetchReceiver", "Error during daily DB fetch", e);
        }
    }
}
