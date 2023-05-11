package org.ole.planet.myplanet;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class StayOnLineWorker extends Worker {
    public StayOnLineWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Perform the "stay online" operation here
        // Return Result.success() if the operation was successful, or Result.failure() if it failed
        return Result.success();
    }
}
