package com.ayb.busticketpos;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared background executor — avoids per-click {@code newSingleThreadExecutor()} churn. */
public final class AppExecutors {
    private static final ExecutorService BG = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AppBg");
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });

    private AppExecutors() {}

    /** An exception escaping a background task would kill the app, so every task is guarded and logged. */
    public static void runBg(Runnable task) {
        BG.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                AppLog.e("AppExecutors", "Background task failed", t);
            }
        });
    }
}
