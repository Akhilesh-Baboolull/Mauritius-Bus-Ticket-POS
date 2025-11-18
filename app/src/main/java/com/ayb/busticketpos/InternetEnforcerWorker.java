package com.ayb.busticketpos;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class InternetEnforcerWorker extends Worker {

    public InternetEnforcerWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        InternetEnforcerReceiver.enforce(getApplicationContext());
        return Result.success();
    }
}
