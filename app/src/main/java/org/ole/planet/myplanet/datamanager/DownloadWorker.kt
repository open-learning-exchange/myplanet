package org.ole.planet.myplanet.datamanager

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.utilities.DownloadUtils
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getSDPathFromUrl
import org.ole.planet.myplanet.utilities.Utilities

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

            val urls = urlSet.toList()
            DownloadUtils.createChannels(context)

            showProgressNotification(0, urls.size, context.getString(R.string.starting_downloads))

            val completed = AtomicInteger(0)
            val results = coroutineScope {
                urls.map { url ->
                    async {
                        val success = try {
                            downloadFile(url)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                        val done = completed.incrementAndGet()
                        showProgressNotification(done, urls.size, context.getString(R.string.downloaded_files, "$done", "${urls.size}"))
                        sendDownloadUpdate(url, success, done >= urls.size, fromSync)
                        success
                    }
                }.awaitAll()
            }

            showCompletionNotification(completed.get(), urls.size, results.any { !it })
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private suspend fun downloadFile(url: String): Boolean {
        return try {
            val retrofitInterface = ApiClient.client?.create(ApiInterface::class.java)
            val response = retrofitInterface?.downloadFile(Utilities.header, url)?.execute()

            when {
                response == null -> false
                response.isSuccessful -> {
                    val responseBody = response.body()
                    responseBody?.let {
                        downloadFileBody(it, url)
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

    private fun downloadFileBody(body: ResponseBody, url: String) {
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

                        showProgressNotification(progress, 100, "Downloading ${getFileNameFromUrl(url)} ($progress%)")
                    }
                }
            }
        } finally {
            output.flush()
            output.close()
            bis.close()
        }
        DownloadUtils.updateResourceOfflineStatus(url)
    }

    private fun showProgressNotification(current: Int, total: Int, text: String) {
        val notification = DownloadUtils.buildProgressNotification(
            context,
            current,
            total,
            text,
            forWorker = true
        )

        notificationManager.notify(WORKER_NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(completed: Int, total: Int, hadErrors: Boolean) {
        val notification = DownloadUtils.buildCompletionNotification(
            context,
            completed,
            total,
            hadErrors,
            forWorker = true
        )

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


    companion object {
        const val WORKER_NOTIFICATION_ID = 3
        const val COMPLETION_NOTIFICATION_ID = 4
    }
}
