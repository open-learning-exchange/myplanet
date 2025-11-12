package org.ole.planet.myplanet.datamanager

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.ole.planet.myplanet.MainApplication.Companion.createLog
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.utilities.DownloadUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.FileUtils.availableExternalMemorySize
import org.ole.planet.myplanet.utilities.FileUtils.externalMemoryAvailable
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.UrlUtils.header

class MyDownloadService : Service() {
    private var data = ByteArray(1024 * 4)
    private var outputFile: File? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var notificationManager: NotificationManager? = null
    private var totalFileSize = 0
    private lateinit var preferences: SharedPreferences
    private lateinit var urls: Array<String>
    private var currentIndex = 0
    private var fromSync = false

    private var totalDownloadsCount = 0
    private var completedDownloadsCount = 0

    private val downloadJob = SupervisorJob()
    private val downloadScope = CoroutineScope(downloadJob + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        DownloadUtils.createChannels(this)

        val initialNotification = DownloadUtils.buildInitialNotification(this)
        startForeground(ONGOING_NOTIFICATION_ID, initialNotification)

        val urlsKey = intent?.getStringExtra("urls_key") ?: "url_list_key"
        val urlSet = preferences.getStringSet(urlsKey, emptySet()) ?: emptySet()

        if (urlSet.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        urls = urlSet.toTypedArray()
        totalDownloadsCount = urls.size
        fromSync = intent?.getBooleanExtra("fromSync", false) == true

        updateNotificationForBatchDownload()

        downloadScope.launch {
            urls.forEachIndexed { index, url ->
                currentIndex = index
                initDownload(url, fromSync)
            }
        }

        return START_STICKY
    }

    private fun updateNotificationForBatchDownload() {
        DownloadUtils.createChannels(this)
        notificationBuilder = NotificationCompat.Builder(this, "DownloadChannel")
            .setContentTitle(getString(R.string.downloading_files))
            .setContentText("Starting downloads (0/$totalDownloadsCount)")
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(totalDownloadsCount, 0, false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setSilent(true)

        notificationManager?.notify(ONGOING_NOTIFICATION_ID, notificationBuilder?.build())
    }

    private suspend fun initDownload(url: String, fromSync: Boolean) {
        try {
            if (url.isBlank()) {
                downloadFailed("Invalid URL - empty or blank", fromSync)
                return
            }
            
            val retrofitInterface = ApiClient.client.create(ApiInterface::class.java)
            if (retrofitInterface == null) {
                downloadFailed("Network client not available", fromSync)
                return
            }
            
            val authHeader = header
            if (authHeader.isBlank()) {
                downloadFailed("Authentication header not available", fromSync)
                return
            }
            val response = try {
                retrofitInterface.downloadFile(authHeader, url)
            } catch (e: java.net.UnknownHostException) {
                downloadFailed("Server not reachable. Check internet connection.", fromSync)
                return
            } catch (e: java.net.SocketTimeoutException) {
                downloadFailed("Connection timeout. Please try again.", fromSync)
                return
            } catch (e: java.net.ConnectException) {
                downloadFailed("Unable to connect to server", fromSync)
                return
            } catch (e: IOException) {
                downloadFailed("Network error: ${e.localizedMessage ?: "Unknown IO error"}", fromSync)
                return
            } catch (e: Exception) {
                downloadFailed("Network error: ${e.localizedMessage ?: "Unknown error"}", fromSync)
                return
            }

            if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody == null) {
                        downloadFailed("Empty response body", fromSync)
                        return
                    }
                    
                    try {
                        val contentLength = responseBody.contentLength()
                        if (contentLength > 0 && !checkStorage(contentLength)) {
                            downloadFile(responseBody, url)
                        } else if (contentLength == -1L) {
                            downloadFile(responseBody, url)
                        } else if (contentLength == 0L) {
                            downloadFailed("Empty file: Content-Length=$contentLength", fromSync)
                        }
                    } catch (e: Exception) {
                        downloadFailed("Storage check failed: ${e.localizedMessage ?: "Unknown error"}", fromSync)
                    }
            } else {
                val errorMessage = when (response.code()) {
                        401 -> "Unauthorized access"
                        403 -> "Forbidden - Access denied"
                        404 -> "File not found"
                        408 -> "Request timeout"
                        500 -> "Server error"
                        502 -> "Bad gateway"
                        503 -> "Service unavailable"
                        504 -> "Gateway timeout"
                        else -> "Connection failed (${response.code()})"
                }
                downloadFailed(errorMessage, fromSync)

                if (response.code() == 404) {
                    try {
                        val responseString = response.toString()
                        val regex = Regex("url=([^}]*)")
                        val matchResult = regex.find(responseString)
                        val extractedUrl = matchResult?.groupValues?.get(1)
                        createLog("File Not Found", "$extractedUrl")
                    } catch (e: Exception) {
                        createLog("File Not Found", url)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            downloadFailed("Download initialization failed: ${e.localizedMessage ?: "Unknown error"}", fromSync)
        }
    }

    private fun downloadFailed(message: String, fromSync: Boolean) {
        notificationBuilder?.apply {
            setContentText("Error: $message (${currentIndex + 1}/$totalDownloadsCount)")
            notificationManager?.notify(ONGOING_NOTIFICATION_ID, build())
        }

        val download = Download().apply {
            failed = true
            this.message = message
        }
        sendIntent(download, fromSync)
        completedDownloadsCount++

        if (completedDownloadsCount >= totalDownloadsCount) {
            showCompletionNotification(true)
            stopSelf()
        }

        if (!fromSync) {
            if (message == "File Not Found") {
                val intent = Intent(RESOURCE_NOT_FOUND_ACTION)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
        }
    }

    @Throws(IOException::class)
    private fun downloadFile(body: ResponseBody, url: String) {
        val fileSize = body.contentLength()
        outputFile = FileUtils.getSDPathFromUrl(this@MyDownloadService, url)
        var total: Long = 0
        val startTime = System.currentTimeMillis()
        var timeCount = 1

        BufferedInputStream(body.byteStream(), 1024 * 8).use { bis ->
            FileOutputStream(outputFile).use { output ->
                while (true) {
                    val readCount = bis.read(data)
                    if (readCount == -1) break

                    if (readCount > 0) {
                        total += readCount
                        val current = (total / 1024.0).roundToInt().toDouble()
                        val currentTime = System.currentTimeMillis() - startTime

                        val download = Download().apply {
                            fileName = getFileNameFromUrl(url)
                        }

                        if (fileSize > 0) {
                            totalFileSize = (fileSize / 1024.0).toInt()
                            val progress = (total * 100 / fileSize).toInt()
                            this@MyDownloadService.totalFileSize = totalFileSize
                            download.totalFileSize = totalFileSize
                            download.progress = progress
                        } else {
                            download.totalFileSize = 0
                            download.progress = -1
                        }

                        if (currentTime > 1000 * timeCount) {
                            download.currentFileSize = current.toInt()
                            sendNotification(download)
                            timeCount++
                        }
                        output.write(data, 0, readCount)
                    }
                }
            }
        }

        onDownloadComplete(url)
    }

    private fun checkStorage(fileSize: Long): Boolean {
        return when {
            !externalMemoryAvailable() -> {
                downloadFailed("Download Failed: SD card not available", fromSync)
                true
            }
            fileSize > availableExternalMemorySize -> {
                downloadFailed("Download Failed: Not enough storage in SD card", fromSync)
                true
            }
            else -> false
        }
    }

    private fun sendNotification(download: Download) {
        val url = urls.getOrNull(currentIndex) ?: run {
            return
        }

        download.fileName = "Downloading: ${getFileNameFromUrl(url)}"
        sendIntent(download, fromSync)

        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            notificationBuilder?.apply {
                setProgress(totalDownloadsCount, completedDownloadsCount, false)
                setContentText("Downloading ${currentIndex + 1}/$totalDownloadsCount: ${getFileNameFromUrl(url)}")
                notificationManager?.notify(ONGOING_NOTIFICATION_ID, build())
            }
        }
    }

    private fun sendIntent(download: Download, fromSync: Boolean) {
        val intent = Intent(MESSAGE_PROGRESS).apply {
            putExtra("download", download)
            putExtra("fromSync", fromSync)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun onDownloadComplete(url: String) {
        if ((outputFile?.length() ?: 0) > 0) {
            DownloadUtils.updateResourceOfflineStatus(url)
        }
        completedDownloadsCount++

        val download = Download().apply {
            fileName = getFileNameFromUrl(url)
            fileUrl = url
            progress = 100
            completeAll = (completedDownloadsCount >= totalDownloadsCount)
        }

        sendIntent(download, fromSync)
        notificationBuilder?.apply {
            setProgress(totalDownloadsCount, completedDownloadsCount, false)
            setContentText("Downloaded ${completedDownloadsCount}/${totalDownloadsCount} files")
            notificationManager?.notify(ONGOING_NOTIFICATION_ID, build())
        }

        if (completedDownloadsCount >= totalDownloadsCount) {
            showCompletionNotification(false)
            stopSelf()
        }
    }

    private fun showCompletionNotification(hadErrors: Boolean) {
        val notification = DownloadUtils.buildCompletionNotification(
            this,
            completedDownloadsCount,
            totalDownloadsCount,
            hadErrors,
            forWorker = false
        )

        notificationManager?.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        try {
            stopForeground(true)
        } catch (_: Exception) {
        }
        downloadJob.cancel()
        notificationManager?.cancel(ONGOING_NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        const val PREFS_NAME = "MyPrefsFile"
        const val MESSAGE_PROGRESS = "message_progress"
        const val RESOURCE_NOT_FOUND_ACTION = "resource_not_found_action"
        const val ONGOING_NOTIFICATION_ID = 1
        const val COMPLETION_NOTIFICATION_ID = 2

        fun startService(context: Context, urlsKey: String, fromSync: Boolean) {
            val intent = Intent(context, MyDownloadService::class.java).apply {
                putExtra("urls_key", urlsKey)
                putExtra("fromSync", fromSync)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val canStart = when {
                    context is Activity -> true
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                        hasValidForegroundServiceContext(context)
                    }
                    else -> true
                }

                if (canStart) {
                    try {
                        ContextCompat.startForegroundService(context, intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        handleForegroundServiceError(context, urlsKey, fromSync)
                    }
                } else {
                    startDownloadWork(context, urlsKey, fromSync)
                }
            } else {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    handleForegroundServiceError(context, urlsKey, fromSync)
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun hasValidForegroundServiceContext(context: Context): Boolean {
            val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            return activityManager.isBackgroundRestricted.not()
        }

        private fun handleForegroundServiceError(context: Context, urlsKey: String, fromSync: Boolean) {
            try {
                val intent = Intent(context, MyDownloadService::class.java).apply {
                    putExtra("urls_key", urlsKey)
                    putExtra("fromSync", fromSync)
                }
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                startDownloadWork(context, urlsKey, fromSync)
            }
        }

        private fun startDownloadWork(context: Context, urlsKey: String, fromSync: Boolean) {
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(
                    "urls_key" to urlsKey,
                    "fromSync" to fromSync
                ))
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
