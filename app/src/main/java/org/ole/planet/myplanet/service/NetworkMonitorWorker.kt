package org.ole.planet.myplanet.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.ole.planet.myplanet.utilities.NetworkUtils

class NetworkMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_TAG = "network_monitor_work"
        private const val SERVER_REACHABILITY_WORK_TAG = "server_reachability_work"
        // Delay in seconds before starting uploads after network reconnection
        // This gives users time to complete UI operations without blocking
        private const val UPLOAD_DELAY_SECONDS = 30L

        fun start(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<NetworkMonitorWorker>()
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_TAG, ExistingWorkPolicy.KEEP, workRequest)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            var wasConnected = false
            NetworkUtils.isNetworkConnectedFlow.collect { isConnected ->
                if (isConnected && !wasConnected) {
                    scheduleServerReachabilityCheck()
                }
                wasConnected = isConnected
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    private fun scheduleServerReachabilityCheck() {
        Log.d("NetworkMonitorWorker", "Network reconnected - scheduling upload with ${UPLOAD_DELAY_SECONDS}s delay")

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
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

        Log.d("NetworkMonitorWorker", "Background upload scheduled to start in ${UPLOAD_DELAY_SECONDS}s")
    }
}
