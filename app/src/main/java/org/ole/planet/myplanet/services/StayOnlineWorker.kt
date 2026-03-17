package org.ole.planet.myplanet.services

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.utils.Constants.isBetaWifiFeatureEnabled
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NetworkUtils.isWifiConnected

@HiltWorker
class StayOnlineWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val broadcastService: BroadcastService,
    private val dispatcherProvider: DispatcherProvider
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (isBetaWifiFeatureEnabled(context)) {
            if (isWifiConnected()) {
                withContext(dispatcherProvider.io) {
                    broadcastService.sendBroadcast(Intent("SHOW_WIFI_ALERT"))
                }
            }
        }
        return Result.success()
    }
}
