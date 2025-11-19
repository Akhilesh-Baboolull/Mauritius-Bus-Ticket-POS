package com.ayb.busticketpos;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Global crash logger for BusTicketPOS.
 *  - Writes logs to Documents/BusTicketPOSLogs/
 *  - Keeps logs for 30 days
 *  - NO ANR WATCHDOG (removed for stability)
 */
public class CrashLogger implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashLogger";
    private static final int LOG_RETENTION_DAYS = 30;
    private static CrashLogger instance;

    private final Context appContext;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    // lightweight single-thread logger
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CrashLogger-Writer");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private CrashLogger(Context context) {
        this.appContext = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    /** Initialize from Application.onCreate() */
    public static void init(Context context) {
        if (instance == null) {
            instance = new CrashLogger(context);
            Thread.setDefaultUncaughtExceptionHandler(instance);
            Log.i(TAG, "CrashLogger initialized");
            instance.cleanupOldLogs(LOG_RETENTION_DAYS);

            // 🚫 REMOVED: ANR watchdog → improves stability & prevents false ANRs
            // instance.startAnrWatchdog();  <-- Gone
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logException("Uncaught exception in " + t.getName(), e);

        // Pass crash to Android normally
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        }
    }

    public static void logError(String tag, Throwable e) {
        if (instance != null) {
            instance.logException(tag, e);
        } else {
            Log.e(tag, "CrashLogger not initialized!", e);
        }
    }

    /** Non-blocking, safe crash logger */
    private synchronized void logException(String tag, Throwable e) {
        logExecutor.execute(() -> {
            try {
                Runtime rt = Runtime.getRuntime();
                float freeFraction = (float) rt.freeMemory() / rt.totalMemory();

                // avoid write during low-memory
                if (freeFraction < 0.05f) {
                    Log.w(TAG, "Skipping log write: low memory");
                    return;
                }

                File logFile = getDailyLogFile();

                try (FileWriter fw = new FileWriter(logFile, true);
                     PrintWriter pw = new PrintWriter(fw)) {

                    String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date());

                    pw.println("===== " + ts + " =====");
                    pw.println("Thread: " + Thread.currentThread().getName());
                    pw.println("Tag: " + tag);
                    pw.println("Message: " + e.getMessage());
                    e.printStackTrace(pw);
                    pw.println();

                    pw.flush();
                    Log.i(TAG, "Logged to " + logFile.getAbsolutePath());

                } catch (OutOfMemoryError oom) {
                    Log.e(TAG, "Skipped log write due to OOM: " + oom.getMessage());
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to write crash log", ex);
                }

            } catch (Throwable t) {
                Log.e(TAG, "CrashLogger internal failure", t);
            }
        });
    }

    private File getDailyLogFile() {
        File docsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS
        );
        File logDir = new File(docsDir, "BusTicketPOSLogs");
        if (!logDir.exists()) logDir.mkdirs();

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());

        return new File(logDir, "crashlog-" + today + ".txt");
    }

    private void cleanupOldLogs(int days) {
        try {
            File docsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS
            );
            File logDir = new File(docsDir, "BusTicketPOSLogs");
            if (!logDir.exists()) return;

            long now = System.currentTimeMillis();
            long ttl = days * 24L * 60L * 60L * 1000L;

            File[] files = logDir.listFiles((dir, name) ->
                    name != null &&
                            name.startsWith("crashlog-") &&
                            name.endsWith(".txt")
            );
            if (files == null) return;

            int deleted = 0;
            for (File f : files) {
                if (now - f.lastModified() > ttl && f.delete()) {
                    deleted++;
                }
            }

            if (deleted > 0) {
                Log.i(TAG, "Cleanup: deleted " + deleted + " old log file(s)");
            }

        } catch (Exception e) {
            Log.w(TAG, "Cleanup failed", e);
        }
    }

    public static File getTodayLogFile() {
        return (instance != null) ? instance.getDailyLogFile() : null;
    }
}
