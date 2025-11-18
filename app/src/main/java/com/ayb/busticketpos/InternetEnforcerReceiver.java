package com.ayb.busticketpos;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

public class InternetEnforcerReceiver extends BroadcastReceiver {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        enforce(context);
    }

    public static void enforce(Context context) {
        ComponentName admin = new ComponentName(context, MyDeviceAdminReceiver.class);
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

        if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
            try {
                // 🧱 1️⃣ Prevent user from enabling Airplane Mode
                dpm.addUserRestriction(admin, UserManager.DISALLOW_AIRPLANE_MODE);
                Log.d("InternetEnforcer", "User restriction: DISALLOW_AIRPLANE_MODE added");

                // 🧱 2️⃣ Prevent user from turning off Mobile Data
                dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
                Log.d("InternetEnforcer", "User restriction: DISALLOW_CONFIG_MOBILE_NETWORKS added");

                // 🧱 3️⃣ Prevent user from turning off Wi-Fi
                dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_WIFI);
                Log.d("InternetEnforcer", "User restriction: DISALLOW_CONFIG_WIFI added");
            } catch (Exception e) {
                Log.e("InternetEnforcer", "Failed to apply restrictions", e);
            }
        } else {
            Log.w("InternetEnforcer", "App is not Device Owner; cannot apply restrictions");
        }

        // ✅ 4️⃣ Ensure Wi-Fi is ON (still allowed for Device Owner)
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && !wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
                Log.d("InternetEnforcer", "Wi-Fi enabled");
            }
        } catch (Exception e) {
            Log.e("InternetEnforcer", "Wi-Fi enable failed", e);
        }
    }
}
