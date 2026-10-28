package com.ayb.busticketpos;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Backup for the daily check: runs every 24h and enqueues a check-only update run if we haven't
 * checked in 20+ hours (e.g. alarm was cleared by OEM or Doze). Installing stays with the
 * 21:00 / 23:59 alarms and Settings "Install now".
 */
public class DailyUpdateBackupWorker extends Worker {

    private static final long STALE_MS = 20L * 3600 * 1000;

    public DailyUpdateBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        long lastCheck = Prefs.getLastUpdateCheckAt(ctx);
        if (System.currentTimeMillis() - lastCheck > STALE_MS) {
            try {
                UpdateScheduler.enqueueCheckOnly(ctx, true);
                android.util.Log.i("DailyUpdateBackup", "Enqueued check-only update (last check > 20h ago)");
            } catch (Exception e) {
                AppLog.w("DailyUpdateBackup", "Enqueue failed", e);
                return Result.retry();
            }
        }
        return Result.success();
    }
}
