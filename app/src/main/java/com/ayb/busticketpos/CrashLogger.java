package com.ayb.busticketpos;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
 * Global crash and error logger for the BusTicketPOS app.
 *  - Writes daily log files to Documents/BusTicketPOSLogs/
 *  - Keeps logs for 30 days, then deletes older ones
 */
public class CrashLogger implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashLogger";
    private static final int LOG_RETENTION_DAYS = 30;
    private static CrashLogger instance;

    private final Context appContext;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    // 🔹 Dedicated lightweight background thread for logging
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CrashLogger-Writer");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private CrashLogger(Context context) {
        this.appContext = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    /** Initialize once from Application.onCreate() */
    public static void init(Context context) {
        if (instance == null) {
            instance = new CrashLogger(context);
            Thread.setDefaultUncaughtExceptionHandler(instance);
            Log.i(TAG, "CrashLogger initialized");
            instance.cleanupOldLogs(LOG_RETENTION_DAYS);
            instance.startAnrWatchdog();
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logException("Uncaught exception in " + t.getName(), e);
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

    private void startAnrWatchdog() {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final long checkInterval = 4000;
        final long timeout = 6000;
        final android.os.PowerManager pm =
                (android.os.PowerManager) appContext.getSystemService(Context.POWER_SERVICE);

        new Thread(() -> {
            final long[] lastResponseTime = {System.currentTimeMillis()};
            Runnable ping = () -> lastResponseTime[0] = System.currentTimeMillis();

            while (true) {
                try {
                    mainHandler.post(ping);
                    Thread.sleep(checkInterval);

                    // ✅ Skip ANR check if device is sleeping / screen off
                    boolean isInteractive = false;
                    if (pm != null) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
                            isInteractive = pm.isInteractive();
                        } else {
                            // Deprecated before API 20, fallback
                            isInteractive = pm.isScreenOn();
                        }
                    }
                    if (!isInteractive) {
                        continue; // device idle → skip check
                    }

                    if (System.currentTimeMillis() - lastResponseTime[0] > timeout) {
                        // 🧠 Main thread may be frozen
                        Thread mainThread = Looper.getMainLooper().getThread();
                        StackTraceElement[] stackTrace = mainThread.getStackTrace();

                        StringBuilder sb = new StringBuilder();
                        for (StackTraceElement e : stackTrace) {
                            sb.append("\n    at ").append(e.toString());
                        }

                        String message = "Main thread unresponsive for > " + timeout + " ms.\n"
                                + "Stack trace:" + sb;

                        logException("ANR Detected", new RuntimeException(message));
                    }
                } catch (InterruptedException ie) {
                    return;
                } catch (Throwable t) {
                    logException("ANR Watchdog Error", t);
                }
            }
        }, "ANR-Watchdog").start();
    }



    /** Non-blocking, OOM-safe logger */
    private synchronized void logException(String tag, Throwable e) {
        // hand work to background thread
        logExecutor.execute(() -> {
            try {
                // 🧠 Skip if heap critically low (<5% free)
                Runtime rt = Runtime.getRuntime();
                if ((float) rt.freeMemory() / rt.totalMemory() < 0.05f) {
                    Log.w(TAG, "Skipping log write: low memory");
                    return;
                }

                File logFile = getDailyLogFile();
                // Catch both regular and OOM errors
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
                    Log.i(TAG, "Error logged to " + logFile.getAbsolutePath());
                } catch (OutOfMemoryError oom) {
                    // Do not crash — just print minimal info to Logcat
                    Log.e(TAG, "Skipped file log due to OOM: " + oom.getMessage());
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to write crash log", ex);
                }

            } catch (Throwable t) {
                // Protect from secondary crashes
                Log.e(TAG, "CrashLogger internal failure", t);
            }
        });
    }

    private File getDailyLogFile() {
        File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File logDir = new File(docsDir, "BusTicketPOSLogs");
        if (!logDir.exists()) logDir.mkdirs();

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return new File(logDir, "crashlog-" + today + ".txt");
    }

    private void cleanupOldLogs(int days) {
        try {
            File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File logDir = new File(docsDir, "BusTicketPOSLogs");
            if (!logDir.exists()) return;

            long now = System.currentTimeMillis();
            long ttl = days * 24L * 60L * 60L * 1000L;

            File[] files = logDir.listFiles((dir, name) ->
                    name != null && name.startsWith("crashlog-") && name.endsWith(".txt"));
            if (files == null) return;

            int deleted = 0;
            for (File f : files) {
                if (now - f.lastModified() > ttl && f.delete()) deleted++;
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
