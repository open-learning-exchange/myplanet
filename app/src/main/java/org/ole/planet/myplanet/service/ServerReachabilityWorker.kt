package org.ole.planet.myplanet.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.datamanager.DatabaseService
import android.util.Log

class ServerReachabilityWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "server_reachability_channel"
        private const val CHANNEL_NAME = "Server Connectivity"
        private const val LAST_NOTIFICATION_TIME_KEY = "last_server_notification_time"
        private const val NOTIFICATION_COOLDOWN_MS = 30 * 60 * 1000L
        private const val NETWORK_RECONNECTION_KEY = "network_reconnection_trigger"
        private const val TAG = "ServerReachabilityWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            if (!NetworkUtils.isNetworkConnected) {
                return Result.success()
            }

            val isNetworkReconnection = inputData.getBoolean(NETWORK_RECONNECTION_KEY, false)
            val preferences = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val serverUrl = preferences.getString("serverURL", "") ?: ""
            
            if (serverUrl.isEmpty()) {
                return Result.success()
            }

            val isReachable = withContext(Dispatchers.IO) {
                isServerReachable(serverUrl)
            }

            if (!isReachable) {
                tryServerSwitch(serverUrl, preferences, isNetworkReconnection)
            }
            
            if (isReachable && isNetworkReconnection) {
                Log.d(TAG, "Server is reachable after network reconnection")
                val lastNotificationTime = preferences.getLong(LAST_NOTIFICATION_TIME_KEY, 0)
                val currentTime = System.currentTimeMillis()
                val timeSinceLastNotification = currentTime - lastNotificationTime
                
                // Always check for unsynced submissions regardless of notification cooldown
//                if (hasUnsyncedSubmissions()) {
                    Log.i(TAG, "Found unsynced submissions, triggering automatic upload")
                    triggerSubmissionUpload()
//                } else {
//                    Log.d(TAG, "No unsynced submissions found")
//                }
                
                // Show notification only if cooldown has passed
                if (timeSinceLastNotification > NOTIFICATION_COOLDOWN_MS) {
                    Log.d(TAG, "Showing server notification (cooldown expired)")
                    showServerNotification(preferences)
                    preferences.edit {
                        putLong(LAST_NOTIFICATION_TIME_KEY, currentTime)
                    }
                } else {
                    Log.d(TAG, "Notification cooldown still active, skipping notification but upload check completed")
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun tryServerSwitch(serverUrl: String, preferences: android.content.SharedPreferences, isNetworkReconnection: Boolean) {
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
                        Log.d(TAG, "Alternative server is reachable after network reconnection")
                        val lastNotificationTime = preferences.getLong(LAST_NOTIFICATION_TIME_KEY, 0)
                        val currentTime = System.currentTimeMillis()
                        val timeSinceLastNotification = currentTime - lastNotificationTime
                        
                        // Always check for unsynced submissions regardless of notification cooldown
                        if (hasUnsyncedSubmissions()) {
                            Log.i(TAG, "Found unsynced submissions with alternative server, triggering automatic upload")
                            triggerSubmissionUpload()
                        } else {
                            Log.d(TAG, "No unsynced submissions found (alternative server)")
                        }
                        
                        // Show notification only if cooldown has passed
                        if (timeSinceLastNotification > NOTIFICATION_COOLDOWN_MS) {
                            Log.d(TAG, "Showing server notification for alternative server (cooldown expired)")
                            showServerNotification(preferences)
                            preferences.edit {
                                putLong(LAST_NOTIFICATION_TIME_KEY, currentTime)
                            }
                        } else {
                            Log.d(TAG, "Alternative server notification cooldown still active, skipping notification but upload check completed")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showServerNotification(preferences: android.content.SharedPreferences) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)
        
        val intent = Intent(applicationContext, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val appName = applicationContext.getString(R.string.app_project_name)
        val serverName = getServerDisplayName(preferences)
        
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
    
    private fun getServerDisplayName(preferences: android.content.SharedPreferences): String {
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
    
    private fun hasUnsyncedSubmissions(): Boolean {
        return try {
            Log.d(TAG, "Checking for unsynced submissions in database")
            val databaseService = DatabaseService(applicationContext)
            val realm = databaseService.realmInstance
            
            realm.use { realmInstance ->
                val unsyncedSubmissions = realmInstance.where(org.ole.planet.myplanet.model.RealmSubmission::class.java)
                    .equalTo("isUpdated", true)
                    .or()
                    .isEmpty("_id")
                    .findAll()
                
                val count = unsyncedSubmissions.size
                Log.d(TAG, "Found $count unsynced submissions")
                count > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for unsynced submissions", e)
            e.printStackTrace()
            false
        }
    }
    
    private fun triggerSubmissionUpload() {
        try {
            Log.i(TAG, "Starting automatic submission upload after server reconnection")
            val uploadManager = UploadManager(applicationContext)
            uploadManager.uploadSubmissions()
            Log.i(TAG, "Automatic submission upload triggered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering automatic submission upload", e)
            e.printStackTrace()
        }
    }
}
