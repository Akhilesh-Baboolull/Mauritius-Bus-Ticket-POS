package com.ayb.busticketpos;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class UpdateScheduler {
    private UpdateScheduler(){}

    public static void enqueueDailyUpdate(Context ctx) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(UpdateWorker.class)
                .setConstraints(constraints)
                // Short delay so update check runs soon after alarm; DB sync runs after update (or on next launch)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(ctx).enqueueUniqueWork(
                "app-update",
                ExistingWorkPolicy.KEEP,
                req
        );
    }
}
