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

    private static volatile SharedPreferences securePrefs;

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
        } catch (RuntimeException e) {
            // Keystore glitch: do not wipe provisioning, just use the plain fallback for this run.
            AppLog.e("PrefsSecure", "Encrypted prefs runtime failure — using fallback without reset", e);
            return appCtx.getSharedPreferences("secure_config_prefs_fallback", Context.MODE_PRIVATE);
        } catch (GeneralSecurityException | IOException e) {
            if (!afterReset) {
                AppLog.e("PrefsSecure", "Encrypted prefs failed — clearing and retrying once", e);
                try {
                    appCtx.deleteSharedPreferences("secure_config_prefs");
                } catch (Exception del) {
                    AppLog.w("PrefsSecure", "Could not delete secure_config_prefs", del);
                }
                return openSecurePrefs(appCtx, true);
            }
            AppLog.e("PrefsSecure", "Encrypted prefs unavailable after reset", e);
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

    /** Reads never throw: a Keystore / decrypt failure returns the default and is logged (nothing is wiped). */
    private static String safeGet(Context context, String key, String def) {
        try {
            return getSecurePrefs(context).getString(key, def);
        } catch (Throwable t) {
            AppLog.e("PrefsSecure", "Could not read " + key, t);
            return def;
        }
    }

    // 🔓 Retrieve
    public static String getTenantId(Context context) {
        return safeGet(context, "TENANT_ID", "");
    }

    public static String getMachineId(Context context) {
        return safeGet(context, "MACHINE_ID", "");
    }

    public static void saveFeedbackNumber(Context context, String phone_number) {
        getSecurePrefs(context).edit().putString("PHONE_NUMBER", phone_number).apply();
    }

    // 🔓 Retrieve
    public static String getFeedbackNumber(Context context) {
        return safeGet(context, "PHONE_NUMBER", "");
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

        } catch (Throwable e) {
            // Log it safely if you like (optional)
            AppLog.e("PrefsSecure", "Error reading TENANT_NAME", e);
            return "AYB WAY";
        }
    }

    public static void saveApiKey(Context context, String apiKey) {
        getSecurePrefs(context).edit().putString("API_KEY", apiKey).apply();
    }

    public static String getApiKey(Context context) {
        return safeGet(context, "API_KEY", "");
    }

    public static boolean hasProvisioning(Context ctx) {
        String t = getTenantId(ctx), m = getMachineId(ctx), k = getApiKey(ctx);
        return t != null && !t.isEmpty() && m != null && !m.isEmpty() && k != null && !k.isEmpty();
    }


}
