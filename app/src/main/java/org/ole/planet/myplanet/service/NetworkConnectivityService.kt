package org.ole.planet.myplanet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.NetworkUtils

class NetworkConnectivityService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wasConnected = false

    companion object {
        private const val SERVER_REACHABILITY_WORK_TAG = "server_reachability_work"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "NetworkConnectivityChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startNetworkMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Connectivity",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors network connectivity for the app"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val communityName = settings.getString("communityName", "")
        val title = if (communityName.isNullOrEmpty()) {
            "myPlanet"
        } else {
            "planet $communityName"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.checking_server_availability))
            .setSmallIcon(R.drawable.ole_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
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
        super.onDestroy()
        serviceScope.cancel()
    }
}
