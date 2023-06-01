package org.ole.planet.myplanet.utilities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class TaskNotificationWorker extends Worker {
    public TaskNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Perform the task notification operation here
        // Return Result.success() if the operation was successful, or Result.failure() if it failed
        return Result.success();
    }
}
