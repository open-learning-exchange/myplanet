package org.ole.planet.myplanet.service

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.utilities.Constants.isBetaWifiFeatureEnabled
import org.ole.planet.myplanet.utilities.NetworkUtils.isWifiConnected

class StayOnlineWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        if (isBetaWifiFeatureEnabled(context)) {
            if (isWifiConnected()) {
                withContext(Dispatchers.IO) {
                    val broadcastService = getBroadcastService(context)
                    broadcastService.sendBroadcast(Intent("SHOW_WIFI_ALERT"))
                }
            }
        }
        return Result.success()
    }
}
