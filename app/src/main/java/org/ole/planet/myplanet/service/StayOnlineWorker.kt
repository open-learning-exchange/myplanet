package org.ole.planet.myplanet.service

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.NetworkUtils.isWifiConnected

class StayOnlineWorker(private val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        if (showBetaFeature(Constants.KEY_SYNC, context)) {
            if (isWifiConnected()) {
                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent("SHOW_WIFI_ALERT"))
            }
        }
        return Result.success()
    }
}