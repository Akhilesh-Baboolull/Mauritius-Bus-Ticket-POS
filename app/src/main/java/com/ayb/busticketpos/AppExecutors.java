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

    public static void runBg(Runnable task) {
        BG.execute(task);
    }
}
