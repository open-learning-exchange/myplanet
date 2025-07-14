package org.ole.planet.myplanet.datamanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.realm.Realm
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getSDPathFromUrl

class DownloadWorker(val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val preferences = context.getSharedPreferences(MyDownloadService.PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val urlsKey = inputData.getString("urls_key") ?: "url_list_key"
            val fromSync = inputData.getBoolean("fromSync", false)
            val urlSet = preferences.getStringSet(urlsKey, emptySet()) ?: emptySet()

            if (urlSet.isEmpty()) {
                return@withContext Result.failure()
            }

            val urls = urlSet.toTypedArray()
            initializeNotificationChannels()

            showProgressNotification(0, urls.size, context.getString(R.string.starting_downloads))

            var completedCount = 0
            val results = mutableListOf<Boolean>()

            urls.forEachIndexed { index, url ->
                try {
                    val success = downloadFile(url, index, urls.size)
                    results.add(success)
                    completedCount++

                    showProgressNotification(completedCount, urls.size, context.getString(R.string.downloaded_files, "$completedCount", "${urls.size}"))
                    sendDownloadUpdate(url, success, completedCount >= urls.size, fromSync)
                } catch (e: Exception) {
                    e.printStackTrace()
                    results.add(false)
                    completedCount++
                }
            }

            showCompletionNotification(completedCount, urls.size, results.any { !it })
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private suspend fun downloadFile(url: String, index: Int, total: Int): Boolean {
        return try {
            val retrofitInterface = ApiClient.client?.create(ApiInterface::class.java)
            val response = retrofitInterface?.downloadFile(Utilities.header, url)?.execute()

            when {
                response == null -> false
                response.isSuccessful -> {
                    val responseBody = response.body()
                    responseBody?.let {
                        downloadFileBody(it, url, index, total)
                        true
                    } == true
                }
                else -> {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun downloadFileBody(body: ResponseBody, url: String, index: Int, total: Int) {
        val fileSize = body.contentLength()
        val bis = BufferedInputStream(body.byteStream(), 1024 * 8)
        val outputFile = getSDPathFromUrl(url)
        val output = FileOutputStream(outputFile)
        val data = ByteArray(1024 * 4)
        var totalBytes: Long = 0

        try {
            while (true) {
                val readCount = bis.read(data)
                if (readCount == -1) break

                if (readCount > 0) {
                    totalBytes += readCount
                    output.write(data, 0, readCount)

                    if (totalBytes % (1024 * 100) == 0L) {
                        val progress = if (fileSize > 0) {
                            (totalBytes * 100 / fileSize).toInt()
                        } else 0

                        showProgressNotification(index, total, "Downloading ${getFileNameFromUrl(url)} ($progress%)")
                    }
                }
            }
        } finally {
            output.flush()
            output.close()
            bis.close()
        }
        changeOfflineStatus(url)
    }

    private fun initializeNotificationChannels() {
        val channelId = "DownloadWorkerChannel"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, "Background Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress for background downloads"
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(current: Int, total: Int, text: String) {
        val notification = NotificationCompat.Builder(applicationContext, "DownloadWorkerChannel")
            .setContentTitle(context.getString(R.string.downloading_files))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(total, current, false)
            .setOngoing(true)
            .setSilent(true)
            .build()

        notificationManager.notify(WORKER_NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(completed: Int, total: Int, hadErrors: Boolean) {
        val notification = NotificationCompat.Builder(applicationContext, "DownloadWorkerChannel")
            .setContentTitle(context.getString(R.string.downloads_completed))
            .setContentText(
                if (hadErrors) {
                    context.getString(R.string.download_progress_with_errors, completed, total)
                } else {
                    context.getString(R.string.download_progress, completed, total)
                }
            )
            .setSmallIcon(R.drawable.ic_download)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    private fun sendDownloadUpdate(url: String, success: Boolean, isComplete: Boolean, fromSync: Boolean) {
        val download = Download().apply {
            fileName = getFileNameFromUrl(url)
            fileUrl = url
            progress = if (success) 100 else 0
            failed = !success
            completeAll = isComplete
            if (!success) {
                message = context.getString(R.string.download_failed)
            }
        }

        val intent = Intent(MyDownloadService.MESSAGE_PROGRESS).apply {
            putExtra("download", download)
            putExtra("fromSync", fromSync)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun changeOfflineStatus(url: String) {
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

    companion object {
        const val WORKER_NOTIFICATION_ID = 3
        const val COMPLETION_NOTIFICATION_ID = 4
    }
}
