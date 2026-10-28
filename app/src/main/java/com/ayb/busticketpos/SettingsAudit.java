package com.ayb.busticketpos;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Audit trail for everything done on the Settings screen.
 * Each event is kept on the device for {@link #RETENTION_MS} and sent to
 * {@link ApiEndpoints#SETTINGS_AUDIT} (queued for retry when offline).
 */
public final class SettingsAudit {

    private static final String TAG = "SettingsAudit";
    private static final String FILE_NAME = "settings_audit_log.jsonl";
    private static final String FILE_NAME_BG = "settings_audit_log_bg.jsonl";
    public static final long RETENTION_MS = 10L * 24 * 60 * 60 * 1000;
    private static final Object FILE_LOCK = new Object();

    private SettingsAudit() {}

    public enum Category {
        INFO(1, "Info"),
        WARNING(2, "Warning"),
        ERROR(3, "Error"),
        CRITICAL(4, "Critical");

        public final int number;
        public final String label;

        Category(int number, String label) {
            this.number = number;
            this.label = label;
        }

        public static Category fromNumber(int n) {
            for (Category c : values()) {
                if (c.number == n) return c;
            }
            return INFO;
        }
    }

    public enum Event {
        LOGIN_SUCCESS("SETTINGS_LOGIN_SUCCESS", "Logged in to settings", Category.INFO),
        LOGIN_FAILED("SETTINGS_LOGIN_FAILED", "Failed login to settings", Category.ERROR),
        SYNC_QUEUE_CLEARED("SYNC_QUEUE_CLEARED", "Cleared sync queue", Category.WARNING),
        REGISTRATION_OPENED("REGISTRATION_DIALOG_OPENED", "Opened device registration", Category.INFO),
        DEVICE_REGISTERED("DEVICE_REGISTERED", "Registered device", Category.INFO),
        REGISTRATION_FAILED("DEVICE_REGISTRATION_FAILED", "Failed to register device", Category.ERROR),
        ADMIN_ACCESS_UNLOCKED("ADMIN_ACCESS_UNLOCKED", "Unlocked admin access", Category.CRITICAL),
        ADMIN_ACCESS_FAILED("ADMIN_ACCESS_FAILED", "Failed to unlock admin access", Category.ERROR),

        // Other Settings actions
        ADMIN_ACCESS_OPENED("ADMIN_ACCESS_DIALOG_OPENED", "Opened admin access", Category.INFO),
        UPDATE_CHECK_REQUESTED("UPDATE_CHECK_REQUESTED", "Checked for updates", Category.INFO),
        UPDATE_INSTALL_REQUESTED("UPDATE_INSTALL_REQUESTED", "Started update install", Category.WARNING),
        MASTER_SYNC_SUCCESS("MASTER_SYNC_SUCCESS", "Synced routes & fares", Category.INFO),
        MASTER_SYNC_FAILED("MASTER_SYNC_FAILED", "Failed to sync routes & fares", Category.ERROR),
        PENDING_UPLOAD_REQUESTED("PENDING_UPLOAD_REQUESTED", "Sent pending data", Category.INFO),
        PENDING_ITEMS_VIEWED("PENDING_ITEMS_VIEWED", "Viewed pending uploads", Category.INFO),
        BRIGHTNESS_CHANGED("BRIGHTNESS_CHANGED", "Changed screen brightness", Category.INFO),
        AUDIT_LOG_VIEWED("AUDIT_LOG_VIEWED", "Viewed audit log", Category.INFO),
        AUDIT_LOG_EXPORTED("AUDIT_LOG_EXPORTED", "Exported audit log", Category.INFO),

        // App-wide errors and warnings (see AppLog)
        APP_WARNING("APP_WARNING", "App warning", Category.WARNING),
        APP_ERROR("APP_ERROR", "App error", Category.ERROR),
        APP_CRASH("APP_CRASH", "App crash", Category.CRITICAL);

        public final String code;
        public final String title;
        public final Category category;

        Event(String code, String title, Category category) {
            this.code = code;
            this.title = title;
            this.category = category;
        }
    }

    /** One stored audit row. */
    public static final class Entry {
        public final long timeMillis;
        public final String code;
        public final String title;
        public final Category category;
        @Nullable public final String description;

        Entry(long timeMillis, String code, String title, Category category, @Nullable String description) {
            this.timeMillis = timeMillis;
            this.code = code;
            this.title = title;
            this.category = category;
            this.description = description;
        }
    }

    public static void log(Context ctx, Event event) {
        log(ctx, event, null, null);
    }

    public static void log(Context ctx, Event event, @Nullable String description) {
        log(ctx, event, description, null);
    }

    /** Safe from the main thread: file and network work run in the background. Always sent online. */
    public static void log(Context ctx, Event event, @Nullable String description, @Nullable JSONObject details) {
        log(ctx, event, description, details, true);
    }

    /** @param sendOnline false keeps the event on the device only (AppLog warnings / rate-limited errors). */
    public static void log(Context ctx, Event event, @Nullable String description,
                           @Nullable JSONObject details, boolean sendOnline) {
        final Context app = ctx.getApplicationContext();
        final long now = System.currentTimeMillis();
        Log.i(TAG, event.code + " [" + event.category.label + "] " + (description != null ? description : ""));

        AppExecutors.runBg(() -> {
            try {
                appendLocal(app, localJson(event, description, now), now);
            } catch (Exception e) {
                Log.w(TAG, "local audit write failed", e);
            }

            if (!sendOnline) return;
            try {
                JSONObject online = new JSONObject()
                        .put("event", event.code)
                        .put("event_name", event.title)
                        .put("category", event.category.number)
                        .put("category_name", event.category.label)
                        .put("description", description != null ? description : JSONObject.NULL)
                        .put("event_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                .format(new Date(now)))
                        .put("details", details != null ? details : JSONObject.NULL);
                // :bg has no WorkManager, so it cannot use the sync queue as a fallback.
                SyncClient.sendSettingsAudit(app, online, !App.isBgProcess(app));
            } catch (Exception e) {
                Log.w(TAG, "online audit build failed", e);
            }
        });
    }

    /** Writes on the calling thread. Used by the crash handler, where the process is about to die. */
    public static void logNowLocalOnly(Context ctx, Event event, @Nullable String description) {
        try {
            long now = System.currentTimeMillis();
            appendLocal(ctx.getApplicationContext(), localJson(event, description, now), now);
        } catch (Throwable ignored) {
        }
    }

    private static JSONObject localJson(Event event, @Nullable String description, long now) throws Exception {
        return new JSONObject()
                .put("t", now)
                .put("event", event.code)
                .put("title", event.title)
                .put("category", event.category.number)
                .put("description", description != null ? description : JSONObject.NULL);
    }

    /** Last {@link #RETENTION_MS} of entries from both processes, newest first. Call off the main thread. */
    public static List<Entry> readRecent(Context ctx) {
        List<Entry> out = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        Context app = ctx.getApplicationContext();
        synchronized (FILE_LOCK) {
            for (File f : new File[]{new File(app.getFilesDir(), FILE_NAME),
                    new File(app.getFilesDir(), FILE_NAME_BG)}) {
                for (String line : readLines(f)) {
                    try {
                        JSONObject o = new JSONObject(line);
                        long t = o.optLong("t", 0);
                        if (t < cutoff) continue;
                        String desc = o.isNull("description") ? null : o.optString("description", null);
                        out.add(new Entry(t,
                                o.optString("event", ""),
                                o.optString("title", ""),
                                Category.fromNumber(o.optInt("category", 1)),
                                desc));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.timeMillis, a.timeMillis));
        return out;
    }

    private static final int MAX_LINES = 5000;
    private static final long PRUNE_INTERVAL_MS = 60L * 60 * 1000;
    private static long lastPruneMs = 0L;

    /** Appends one line; every hour (and on first write) drops entries older than 10 days and caps the file. */
    private static void appendLocal(Context ctx, JSONObject entry, long now) throws Exception {
        synchronized (FILE_LOCK) {
            File f = file(ctx);
            if (now - lastPruneMs < PRUNE_INTERVAL_MS) {
                try (FileOutputStream fos = new FileOutputStream(f, true)) {
                    fos.write((entry + "\n").getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            lastPruneMs = now;

            long cutoff = now - RETENTION_MS;
            List<String> kept = new ArrayList<>();
            for (String line : readLines(f)) {
                try {
                    if (new JSONObject(line).optLong("t", 0) >= cutoff) kept.add(line);
                } catch (Exception ignored) {
                }
            }
            kept.add(entry.toString());
            int from = Math.max(0, kept.size() - MAX_LINES);
            try (FileOutputStream fos = new FileOutputStream(f, false)) {
                for (int i = from; i < kept.size(); i++) {
                    fos.write((kept.get(i) + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    private static List<String> readLines(File f) {
        List<String> lines = new ArrayList<>();
        if (!f.exists()) return lines;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) lines.add(line);
            }
        } catch (Exception e) {
            Log.w(TAG, "read audit log failed", e);
        }
        return lines;
    }

    /** Each process writes its own file so the two never interleave lines. */
    private static File file(Context ctx) {
        Context app = ctx.getApplicationContext();
        return new File(app.getFilesDir(), App.isBgProcess(app) ? FILE_NAME_BG : FILE_NAME);
    }
}
