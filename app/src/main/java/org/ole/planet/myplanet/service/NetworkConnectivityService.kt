package org.ole.planet.myplanet.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.ole.planet.myplanet.utilities.NetworkUtils

class NetworkConnectivityService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wasConnected = false

    companion object {
        private const val TAG = "NetworkConnectivityService"
        private const val SERVER_REACHABILITY_WORK_TAG = "server_reachability_work"
    }

    override fun onCreate() {
        super.onCreate()
        startNetworkMonitoring()
    }

    private fun startNetworkMonitoring() {
        NetworkUtils.isNetworkConnectedFlow.onEach { isConnected ->
            if (isConnected && !wasConnected) {
                scheduleServerReachabilityCheck()
            }
            wasConnected = isConnected
        }.launchIn(serviceScope)
    }

    private fun scheduleServerReachabilityCheck() {
        val inputData = Data.Builder()
            .putBoolean("network_reconnection_trigger", true)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<ServerReachabilityWorker>()
            .addTag(SERVER_REACHABILITY_WORK_TAG)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                SERVER_REACHABILITY_WORK_TAG, ExistingWorkPolicy.REPLACE, workRequest
            )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
