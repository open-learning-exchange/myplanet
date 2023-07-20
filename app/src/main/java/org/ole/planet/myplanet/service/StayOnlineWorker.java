package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.NetworkUtils;

public class StayOnlineWorker extends Worker {
    private Context context;

    public StayOnlineWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        if (Constants.showBetaFeature(Constants.KEY_SYNC, context)) {
            if (NetworkUtils.isWifiConnected()) {
                LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("SHOW_WIFI_ALERT"));
            }
        }

        return Result.success();
    }
}