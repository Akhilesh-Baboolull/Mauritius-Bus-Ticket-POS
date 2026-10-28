package com.ayb.busticketpos;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Drop-in replacement for {@link Log#w} / {@link Log#e} that also records the message in the
 * Audit Log (Settings &gt; Audit Log). Repeats of the same message are merged, and only Error /
 * Critical entries are sent online, with a rate limit, so a noisy loop cannot flood the log
 * or the server.
 */
public final class AppLog {

    private static final String TAG = "AppLog";
    private static final long MERGE_WINDOW_MS = 10L * 60 * 1000;
    private static final long ONLINE_PER_KEY_MS = 60L * 60 * 1000;
    private static final long ONLINE_WINDOW_MS = 60L * 60 * 1000;
    private static final int ONLINE_MAX_PER_WINDOW = 30;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_TRACKED_KEYS = 300;

    private static volatile Context appContext;

    private static final Object LOCK = new Object();
    /** key -> {window start millis, repeats suppressed in the window} */
    private static final Map<String, long[]> recent = new HashMap<>();
    private static final Map<String, Long> lastOnlineByKey = new HashMap<>();
    private static final ArrayDeque<Long> onlineTimes = new ArrayDeque<>();

    private AppLog() {}

    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
    }

    // ---- Same shapes as android.util.Log ----------------------------------------------------

    public static int w(String tag, String msg) {
        int r = Log.w(tag, msg);
        record(SettingsAudit.Event.APP_WARNING, tag, msg, null);
        return r;
    }

    public static int w(String tag, String msg, @Nullable Throwable t) {
        int r = Log.w(tag, msg, t);
        record(SettingsAudit.Event.APP_WARNING, tag, msg, t);
        return r;
    }

    public static int w(String tag, @Nullable Throwable t) {
        int r = Log.w(tag, t);
        record(SettingsAudit.Event.APP_WARNING, tag, null, t);
        return r;
    }

    public static int e(String tag, String msg) {
        int r = Log.e(tag, msg);
        record(SettingsAudit.Event.APP_ERROR, tag, msg, null);
        return r;
    }

    public static int e(String tag, String msg, @Nullable Throwable t) {
        int r = Log.e(tag, msg, t);
        record(SettingsAudit.Event.APP_ERROR, tag, msg, t);
        return r;
    }

    /** Uncaught exception: written synchronously because the process is about to end. */
    public static void crash(String threadName, Throwable t) {
        Context ctx = appContext;
        if (ctx == null || t == null) return;
        try {
            SettingsAudit.logNowLocalOnly(ctx, SettingsAudit.Event.APP_CRASH,
                    describe("Uncaught", "Crash in thread " + threadName, t, 0));
        } catch (Throwable ignored) {
        }
    }

    // ---- Internals ---------------------------------------------------------------------------

    private static void record(SettingsAudit.Event event, String tag, @Nullable String msg, @Nullable Throwable t) {
        Context ctx = appContext;
        if (ctx == null) return;
        try {
            String key = event.code + "|" + tag + "|" + (msg != null ? msg.replaceAll("\\d+", "#") : "")
                    + "|" + (t != null ? t.getClass().getName() : "");
            long now = System.currentTimeMillis();
            long repeated;
            boolean online;
            synchronized (LOCK) {
                long[] state = recent.get(key);
                if (state != null && now - state[0] < MERGE_WINDOW_MS) {
                    state[1]++;
                    return;
                }
                repeated = state != null ? state[1] : 0;
                if (recent.size() >= MAX_TRACKED_KEYS) evictOld(now);
                recent.put(key, new long[]{now, 0});
                online = event.category != SettingsAudit.Category.WARNING && allowOnline(key, now);
            }

            SettingsAudit.log(ctx, event, describe(tag, msg, t, repeated), details(tag, t), online);
        } catch (Throwable ignored) {
            // Logging must never crash the app.
        }
    }

    private static boolean allowOnline(String key, long now) {
        Long last = lastOnlineByKey.get(key);
        if (last != null && now - last < ONLINE_PER_KEY_MS) return false;
        while (!onlineTimes.isEmpty() && now - onlineTimes.peekFirst() > ONLINE_WINDOW_MS) {
            onlineTimes.pollFirst();
        }
        if (onlineTimes.size() >= ONLINE_MAX_PER_WINDOW) return false;
        onlineTimes.addLast(now);
        lastOnlineByKey.put(key, now);
        return true;
    }

    private static void evictOld(long now) {
        Iterator<Map.Entry<String, long[]>> it = recent.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue()[0] >= MERGE_WINDOW_MS) it.remove();
        }
        if (recent.size() >= MAX_TRACKED_KEYS) recent.clear();
        if (lastOnlineByKey.size() >= MAX_TRACKED_KEYS) lastOnlineByKey.clear();
    }

    private static String describe(String tag, @Nullable String msg, @Nullable Throwable t, long repeated) {
        StringBuilder sb = new StringBuilder("[").append(tag).append("] ");
        if (msg != null) sb.append(msg);
        if (t != null) {
            if (msg != null) sb.append(": ");
            sb.append(t.getClass().getSimpleName());
            if (t.getMessage() != null) sb.append(": ").append(t.getMessage());
        }
        if (repeated > 0) sb.append(" (repeated ").append(repeated).append(" times)");
        String s = sb.toString();
        return s.length() > MAX_DESCRIPTION ? s.substring(0, MAX_DESCRIPTION) : s;
    }

    private static JSONObject details(String tag, @Nullable Throwable t) {
        JSONObject d = new JSONObject();
        try {
            d.put("tag", tag);
            d.put("process", Application.getProcessName());
            if (t != null) {
                d.put("exception", t.getClass().getName());
                StringBuilder stack = new StringBuilder();
                StackTraceElement[] st = t.getStackTrace();
                for (int i = 0; i < Math.min(5, st.length); i++) {
                    stack.append(st[i]).append('\n');
                }
                d.put("stack", stack.toString());
            }
        } catch (Throwable ignored) {
        }
        return d;
    }
}
