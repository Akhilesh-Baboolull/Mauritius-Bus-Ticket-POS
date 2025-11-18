package com.ayb.busticketpos;

import android.content.Context;

import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;

import java.util.concurrent.TimeUnit;

public class InternetEnforcerScheduler {

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                InternetEnforcerWorker.class,
                15, TimeUnit.MINUTES // Minimum allowed by Android
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "InternetEnforcerJob",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        );
    }
}
