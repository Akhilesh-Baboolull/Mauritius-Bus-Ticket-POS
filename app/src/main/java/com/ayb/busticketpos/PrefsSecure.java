package com.ayb.busticketpos;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class PrefsSecure {

    private static SharedPreferences securePrefs;

    public static SharedPreferences getSecurePrefs(Context context) {
        if (securePrefs == null) {
            synchronized (PrefsSecure.class) {
                if (securePrefs == null) {
                    securePrefs = openSecurePrefs(context.getApplicationContext(), false);
                }
            }
        }
        return securePrefs;
    }

    private static SharedPreferences openSecurePrefs(Context appCtx, boolean afterReset) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    "secure_config_prefs",
                    masterKeyAlias,
                    appCtx,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            if (!afterReset) {
                Log.e("PrefsSecure", "Encrypted prefs failed — clearing and retrying once", e);
                try {
                    appCtx.deleteSharedPreferences("secure_config_prefs");
                } catch (Exception del) {
                    Log.w("PrefsSecure", "Could not delete secure_config_prefs", del);
                }
                return openSecurePrefs(appCtx, true);
            }
            Log.e("PrefsSecure", "Encrypted prefs unavailable after reset", e);
            return appCtx.getSharedPreferences("secure_config_prefs_fallback", Context.MODE_PRIVATE);
        }
    }

    // 🔒 Save tenant ID
    public static void saveTenantId(Context context, String tenantId) {
        getSecurePrefs(context).edit().putString("TENANT_ID", tenantId).apply();
    }

    // 🔒 Save machine ID
    public static void saveMachineId(Context context, String machineId) {
        getSecurePrefs(context).edit().putString("MACHINE_ID", machineId).apply();
    }

//    // 🔒 Save TenantName
    public static void saveTenantName(Context context, String tenantName) {
        getSecurePrefs(context).edit().putString("TENANT_NAME", tenantName).apply();
    }

    // 🔓 Retrieve
    public static String getTenantId(Context context) {
        return getSecurePrefs(context).getString("TENANT_ID", "");
    }

    public static String getMachineId(Context context) {
        return getSecurePrefs(context).getString("MACHINE_ID", "");
    }

    public static void saveFeedbackNumber(Context context, String phone_number) {
        getSecurePrefs(context).edit().putString("PHONE_NUMBER", phone_number).apply();
    }

    // 🔓 Retrieve
    public static String getFeedbackNumber(Context context) {
        return getSecurePrefs(context).getString("PHONE_NUMBER", "");
    }

    public static String getTenantName(Context context) {
        try {
            if (context == null) return "AYB WAY";

            SharedPreferences prefs = getSecurePrefs(context);
            String name = prefs.getString("TENANT_NAME", null);

            if (name == null || name.trim().isEmpty()) {
                return "AYB WAY";
            }

            return name.trim();

        } catch (Exception e) {
            // Log it safely if you like (optional)
            Log.e("PrefsSecure", "Error reading TENANT_NAME", e);
            return "AYB WAY";
        }
    }

    public static void saveApiKey(Context context, String apiKey) {
        getSecurePrefs(context).edit().putString("API_KEY", apiKey).apply();
    }

    public static String getApiKey(Context context) {
        return getSecurePrefs(context).getString("API_KEY", "");
    }

    public static boolean hasProvisioning(Context ctx) {
        return !getTenantId(ctx).isEmpty() && !getMachineId(ctx).isEmpty() && !getApiKey(ctx).isEmpty();
    }


}
