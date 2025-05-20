package org.ole.planet.myplanet.datamanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.ole.planet.myplanet.MainApplication.Companion.createLog
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.FileUtils.availableExternalMemorySize
import org.ole.planet.myplanet.utilities.FileUtils.externalMemoryAvailable
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getSDPathFromUrl
import org.ole.planet.myplanet.utilities.Utilities.header
import retrofit2.Call
import java.io.*
import kotlin.math.roundToInt

class MyDownloadService : Service() {
    private var data = ByteArray(1024 * 4)
    private var outputFile: File? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var notificationManager: NotificationManager? = null
    private var totalFileSize = 0
    private lateinit var preferences: SharedPreferences
    private lateinit var urls: Array<String>
    private var currentIndex = 0
    private var request: Call<ResponseBody>? = null
    private var fromSync = false

    private var totalDownloadsCount = 0
    private var completedDownloadsCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        initializeNotificationChannels()
        val initialNotification = NotificationCompat.Builder(this, "DownloadChannel")
            .setContentTitle(getString(R.string.downloading_files))
            .setContentText(getString(R.string.preparing_download))
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, 0, true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setSilent(true)
            .build()

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

        CoroutineScope(Dispatchers.IO).launch {
            urls.forEachIndexed { index, url ->
                currentIndex = index
                initDownload(url, fromSync)
            }
        }

        return START_STICKY
    }

    private fun initializeNotificationChannels() {
        val channelId = "DownloadChannel"
        if (notificationManager?.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, "Download Service", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
            }
            notificationManager?.createNotificationChannel(channel)
        }

        val completionChannelId = "DownloadCompletionChannel"
        if (notificationManager?.getNotificationChannel(completionChannelId) == null) {
            val channel = NotificationChannel(completionChannelId, "Download Completion", NotificationManager.IMPORTANCE_HIGH)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotificationForBatchDownload() {
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

    private fun initDownload(url: String, fromSync: Boolean) {
        val retrofitInterface = ApiClient.client?.create(ApiInterface::class.java)
        try {
            request = retrofitInterface?.downloadFile(header, url)
            val response = request?.clone()?.execute()

            when {
                response == null -> {
                    downloadFailed("Null response from server", fromSync)
                }
                response.isSuccessful -> {
                    val responseBody = response.body()
                    if (!checkStorage(responseBody?.contentLength() ?: 0L)) {
                        responseBody?.let { downloadFile(it, url) }
                    }
                }
                else -> {
                    val message = if (response.code() == 404) "File Not Found" else "Connection failed (${response.code()})"
                    downloadFailed(message, fromSync)

                    val responseString = response.toString()
                    val regex = Regex("url=([^}]*)")
                    val matchResult = regex.find(responseString)

                    val url = matchResult?.groupValues?.get(1)
                    if (response.code() == 404) {
                        createLog("File Not Found", "$url")
                    }
                }
            }
        } catch (e: IOException) {
            downloadFailed(e.localizedMessage ?: "Download failed due to an IO error", fromSync)
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
        val bis: InputStream = BufferedInputStream(body.byteStream(), 1024 * 8)
        outputFile = getSDPathFromUrl(url)
        val output: OutputStream = FileOutputStream(outputFile)
        var total: Long = 0
        val startTime = System.currentTimeMillis()
        var timeCount = 1

        try {
            while (true) {
                val readCount = bis.read(data)
                if (readCount == -1) break

                if (readCount > 0) {
                    total += readCount
                    totalFileSize = (fileSize / 1024.0).toInt()
                    val current = (total / 1024.0).roundToInt().toDouble()
                    val progress = (total * 100 / fileSize).toInt()
                    val currentTime = System.currentTimeMillis() - startTime

                    val download = Download().apply {
                        fileName = getFileNameFromUrl(url)
                        totalFileSize = this@MyDownloadService.totalFileSize
                    }

                    if (currentTime > 1000 * timeCount) {
                        download.currentFileSize = current.toInt()
                        download.progress = progress
                        sendNotification(download)
                        timeCount++
                    }
                    output.write(data, 0, readCount)
                }
            }
        } finally {
            closeStreams(output, bis, url)
        }
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

    @Throws(IOException::class)
    private fun closeStreams(output: OutputStream, bis: InputStream, url: String) {
        output.flush()
        output.close()
        bis.close()
        onDownloadComplete(url)
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
            changeOfflineStatus(url)
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
        val completionChannelId = "DownloadCompletionChannel"
        val completionNotification = NotificationCompat.Builder(this, completionChannelId)
            .setContentTitle("Downloads Completed")
            .setContentText("$completedDownloadsCount of $totalDownloadsCount files downloaded" +
                    if (hadErrors) " (with some errors)" else "")
            .setSmallIcon(R.drawable.ic_download)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager?.notify(COMPLETION_NOTIFICATION_ID, completionNotification.build())
    }

    private fun changeOfflineStatus(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentFileName = getFileNameFromUrl(url)
            try {
                val backgroundRealm = Realm.getDefaultInstance()
                backgroundRealm.use { realm ->
                    realm.executeTransaction {
                        realm.where(RealmMyLibrary::class.java)
                            .equalTo("resourceLocalAddress", currentFileName)
                            .findAll()?.forEach {
                                it.resourceOffline = true
                                it.downloadedRev = it._rev
                            }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
