package com.ayb.busticketpos;

import android.content.Context;
import android.os.Debug;
import android.util.Log;

/**
 * Ultra-optimized low-overhead RAM watchdog.
 * Monitors memory pressure and restarts only the background service
 * when RAM usage becomes dangerously high.
 */
public final class MemoryWatchdog {

    private static final String TAG = "MemoryWatchdog";

    // Singleton
    private static MemoryWatchdog instance;

    private final Context appCtx;
    private volatile boolean running = true;

    // Only restart service when PSS >= CRITICAL MB
    private static final int WARNING_MB  = 240;   // warn only once
    private static final int CRITICAL_MB = 300;   // restart service

    // Internal guard
    private boolean warnedOnce = false;

    private MemoryWatchdog(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        startThread();
    }

    public static void init(Context ctx) {
        if (instance == null) {
            instance = new MemoryWatchdog(ctx);
        }
    }

    /** Ultra-light monitoring loop */
    private void startThread() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        Thread.sleep(30000); // 30 seconds

                        // Read PSS — fast, cheap, no allocations
                        long pssKb = Debug.getPss();
                        long mb = pssKb >> 10; // divide by 1024 (no floating point)

                        // NORMAL → do nothing
                        if (mb < WARNING_MB) {
                            warnedOnce = false;
                            continue;
                        }

                        // WARNING → log ONCE then continue watching
                        if (mb >= WARNING_MB && mb < CRITICAL_MB) {
                            if (!warnedOnce) {
                                warnedOnce = true;
                                Log.w(TAG, "High RAM usage: " + mb + " MB");
                            }
                            continue;
                        }

                        // CRITICAL → restart service silently
                        Log.e(TAG, "CRITICAL RAM USAGE: " + mb + " MB — restarting service");

                        restartLocationService();

                    } catch (InterruptedException ie) {
                        return; // exiting
                    } catch (Throwable th) {
                        Log.e(TAG, "Watchdog error", th);
                    }
                }
            }
        }, "MemoryWatchdog");

        t.setPriority(Thread.MIN_PRIORITY);
        t.setDaemon(true); // auto-killed when process dies
        t.start();
    }

    /** Stops only background service (freeing 40–100MB) */
    private void restartLocationService() {
        try {
            LocationForegroundService.stop(appCtx);
            Thread.sleep(1000); // allow GC to reclaim memory
            LocationForegroundService.startIfNeeded(appCtx);
        } catch (Throwable ignore) {
        }
    }

    public static void stop() {
        if (instance != null) instance.running = false;
    }
}
