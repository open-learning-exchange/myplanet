package org.ole.planet.myplanet;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AutoSyncWorker extends Worker {
    public AutoSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Perform the auto sync operation here
        // Return Result.success() if the sync was successful, or Result.failure() if it failed
        return Result.success();
    }
}

