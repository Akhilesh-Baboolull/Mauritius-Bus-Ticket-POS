package com.ayb.busticketpos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class UnlockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
            Log.d("UnlockReceiver", "User unlocked device — restarting LocationForegroundService");
            LocationForegroundService.startIfNeeded(context);
        }
    }
}
