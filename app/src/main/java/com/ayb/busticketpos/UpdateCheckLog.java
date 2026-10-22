package com.ayb.busticketpos;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local append-only log for each update check and its outcome.
 * Stored in app files dir as update_check_log.txt; trimmed to last MAX_LINES.
 */
public final class UpdateCheckLog {

    private static final String FILENAME = "update_check_log.txt";
    private static final int MAX_LINES = 500;

    /** Append one entry: timestamp | status | detail (detail may be empty). */
    public static void log(Context ctx, String status, String detail) {
        if (ctx == null || status == null) return;
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                + " | " + status
                + (detail != null && !detail.isEmpty() ? " | " + detail : "")
                + "\n";
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        File dir = ctx.getFilesDir();
        if (dir == null) return;
        File file = new File(dir, FILENAME);
        try {
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write(bytes);
            }
            trimIfNeeded(file);
        } catch (Exception e) {
            android.util.Log.w("UpdateCheckLog", "append failed", e);
        }
    }

    private static void trimIfNeeded(File file) {
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "rwd");
            long len = raf.length();
            if (len <= 0) {
                raf.close();
                return;
            }
            int lines = 0;
            for (long i = len - 1; i >= 0 && lines < MAX_LINES; i--) {
                raf.seek(i);
                if (raf.readByte() == '\n') lines++;
            }
            raf.close();
            if (lines < MAX_LINES) return;
            // Read content, keep last MAX_LINES, rewrite
            byte[] all = java.nio.file.Files.readAllBytes(file.toPath());
            String s = new String(all, StandardCharsets.UTF_8);
            String[] parts = s.split("\n", -1);
            if (parts.length <= MAX_LINES) return;
            StringBuilder sb = new StringBuilder();
            for (int i = parts.length - MAX_LINES; i < parts.length; i++) {
                sb.append(parts[i]).append("\n");
            }
            java.nio.file.Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
