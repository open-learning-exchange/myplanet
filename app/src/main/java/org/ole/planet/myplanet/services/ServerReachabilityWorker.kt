package org.ole.planet.myplanet.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.retry.RetryQueueWorker
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utils.NetworkUtils

@HiltWorker
class ServerReachabilityWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    @AppPreferences private val preferences: SharedPreferences,
    private val uploadManager: UploadManager,
    private val submissionsRepository: SubmissionsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "server_reachability_channel"
        private const val CHANNEL_NAME = "Server Connectivity"
        private const val LAST_NOTIFICATION_TIME_KEY = "last_server_notification_time"
        private const val NOTIFICATION_COOLDOWN_MS = 30 * 60 * 1000L
        private const val NETWORK_RECONNECTION_KEY = "network_reconnection_trigger"
    }

    override suspend fun doWork(): Result {
        return try {
            if (!NetworkUtils.isNetworkConnected) {
                return Result.success()
            }

            val isNetworkReconnection = inputData.getBoolean(NETWORK_RECONNECTION_KEY, false)
            val serverUrl = preferences.getString("serverURL", "") ?: ""

            if (serverUrl.isEmpty()) {
                return Result.success()
            }

            val isReachable = withContext(Dispatchers.IO) {
                isServerReachable(serverUrl)
            }

            if (!isReachable) {
                tryServerSwitch(serverUrl, isNetworkReconnection)
            }

            if (isReachable && isNetworkReconnection) {
                val lastNotificationTime = preferences.getLong(LAST_NOTIFICATION_TIME_KEY, 0)
                val currentTime = System.currentTimeMillis()
                val timeSinceLastNotification = currentTime - lastNotificationTime
                if (timeSinceLastNotification > NOTIFICATION_COOLDOWN_MS) {
                    showServerNotification()
                    preferences.edit {
                        putLong(LAST_NOTIFICATION_TIME_KEY, currentTime)
                    }
                }
                checkAvailableServerAndUpload()
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun tryServerSwitch(serverUrl: String, isNetworkReconnection: Boolean) {
        try {
            val serverUrlMapper = ServerUrlMapper()
            val mapping = serverUrlMapper.processUrl(serverUrl)

            if (mapping.alternativeUrl != null) {
                val alternativeReachable = withContext(Dispatchers.IO) {
                    isServerReachable(mapping.alternativeUrl)
                }

                if (alternativeReachable) {
                    serverUrlMapper.updateServerIfNecessary(mapping, preferences) { url ->
                        isServerReachable(url)
                    }

                    if (isNetworkReconnection) {
                        val lastNotificationTime = preferences.getLong(LAST_NOTIFICATION_TIME_KEY, 0)
                        val currentTime = System.currentTimeMillis()
                        val timeSinceLastNotification = currentTime - lastNotificationTime
                        if (timeSinceLastNotification > NOTIFICATION_COOLDOWN_MS) {
                            showServerNotification()
                            preferences.edit {
                                putLong(LAST_NOTIFICATION_TIME_KEY, currentTime)
                            }
                        }
                        checkAvailableServerAndUpload()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showServerNotification() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val intent = Intent(applicationContext, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appName = applicationContext.getString(R.string.app_project_name)
        val serverName = getServerDisplayName()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ole_logo)
            .setContentTitle(appName)
            .setContentText(applicationContext.getString(R.string.is_available, serverName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun checkAvailableServerAndUpload() {
        val updateUrl = "${preferences.getString("serverURL", "")}"
        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(updateUrl)

        try {
            val primaryAvailable = withTimeoutOrNull(15000) {
                isServerReachable(mapping.primaryUrl)
            } ?: false

            val alternativeAvailable = if (mapping.alternativeUrl != null) {
                withTimeoutOrNull(15000) {
                    isServerReachable(mapping.alternativeUrl)
                } ?: false
            } else {
                false
            }

            if (!primaryAvailable && alternativeAvailable) {
                mapping.alternativeUrl?.let { alternativeUrl ->
                    val uri = updateUrl.toUri()
                    val editor = preferences.edit()
                    serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, preferences)
                }
            }
            uploadSubmissions()
        } catch (e: Exception) {
            e.printStackTrace()
            uploadSubmissions()
        }
    }

    private suspend fun uploadSubmissions() {
        try {
            if (submissionsRepository.hasPendingOfflineSubmissions()) {
                withContext(Dispatchers.IO) {
                    uploadManager.uploadSubmissions()
                }
            }
            uploadExamResultWrapper()
            if (!MainApplication.isSyncRunning) {
                RetryQueueWorker.triggerImmediateRetry(applicationContext)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun uploadExamResultWrapper() {
        if (!submissionsRepository.hasPendingExamResults()) {
            return
        }

        try {
            val successListener = object : OnSuccessListener {
                override fun onSuccess(success: String?) {
                    // No UI updates required for background sync completion.
                }
            }
            uploadManager.uploadExamResult(successListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for server connectivity status"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getServerDisplayName(): String {
        return try {
            val communityName = preferences.getString("communityName", "") ?: ""
            val planetString = applicationContext.getString(R.string.planet)

            if (communityName.isNotEmpty()) {
                "$planetString $communityName"
            } else {
                planetString
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Server"
        }
    }
}
