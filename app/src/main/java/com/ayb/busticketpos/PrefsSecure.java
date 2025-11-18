package com.ayb.busticketpos;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class PrefsSecure {

    private static SharedPreferences securePrefs;

    public static SharedPreferences getSecurePrefs(Context context) {
        if (securePrefs == null) {
            try {
                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                securePrefs = EncryptedSharedPreferences.create(
                        "secure_config_prefs",
                        masterKeyAlias,
                        context,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException("Failed to initialize secure preferences", e);
            }
        }
        return securePrefs;
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

}
