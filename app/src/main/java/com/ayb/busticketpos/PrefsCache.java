package com.ayb.busticketpos;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsCache {
    private static final String PREF_NAME = "cache_prefs";
    private static final String KEY_LAST_FETCH = "last_data_fetch";

    public static void saveLastFetchTime(Context ctx, long timestampMillis) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_FETCH, timestampMillis).apply();
    }

    public static long getLastFetchTime(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_FETCH, 0);
    }

    public static boolean shouldFetchAgain(Context ctx) {
        long lastFetch = getLastFetchTime(ctx);
        long now = System.currentTimeMillis();
        long twentyHoursMillis = 20L * 60 * 60 * 1000; // 20 hours in ms
        return (now - lastFetch) > twentyHoursMillis;
    }
}
