package org.ole.planet.myplanet.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@HiltWorker
class NetworkMonitorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_TAG = "network_monitor_work"
        private const val SERVER_REACHABILITY_WORK_TAG = "server_reachability_work"
        private const val UPLOAD_DELAY_SECONDS = 30L

        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<NetworkMonitorWorker>()
                .addTag(WORK_TAG)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_TAG, ExistingWorkPolicy.KEEP, workRequest)
        }
        
        fun observeReconnects(context: Context, isConnected: Flow<Boolean>, scope: CoroutineScope): Job =
            isConnected
                .distinctUntilChanged()
                .drop(1)
                .filter { it }
                .onEach { start(context) }
                .launchIn(scope)
    }

    override suspend fun doWork(): Result {
        return try {
            scheduleServerReachabilityCheck()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun scheduleServerReachabilityCheck() {
        val inputData = Data.Builder()
            .putBoolean("network_reconnection_trigger", true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ServerReachabilityWorker>()
            .addTag(SERVER_REACHABILITY_WORK_TAG)
            .setInputData(inputData)
            .setInitialDelay(UPLOAD_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                SERVER_REACHABILITY_WORK_TAG,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
    }
}
