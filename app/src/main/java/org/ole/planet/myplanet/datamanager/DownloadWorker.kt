package org.ole.planet.myplanet.datamanager

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.Buffer
import okio.buffer
import okio.sink
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.di.ApiInterfaceEntryPoint
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.service.getBroadcastService
import org.ole.planet.myplanet.utilities.DownloadUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.UrlUtils

class DownloadWorker(val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val preferences = context.getSharedPreferences(MyDownloadService.PREFS_NAME, Context.MODE_PRIVATE)
    private val apiInterface: ApiInterface by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApiInterfaceEntryPoint::class.java
        ).apiInterface()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val urlsKey = inputData.getString("urls_key") ?: "url_list_key"
            val fromSync = inputData.getBoolean("fromSync", false)
            val urlSet = preferences.getStringSet(urlsKey, emptySet()) ?: emptySet()

            if (urlSet.isEmpty()) {
                return@withContext Result.failure()
            }

            val urls = urlSet.toTypedArray()
            DownloadUtils.createChannels(context)

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
            val response = apiInterface.downloadFile(UrlUtils.header, url)
            if (response.isSuccessful) {
                response.body()?.let {
                    downloadFileBody(it, url, index, total)
                    true
                } ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun downloadFileBody(body: ResponseBody, url: String, index: Int, total: Int) {
        val fileSize = body.contentLength()
        val outputFile: File = FileUtils.getSDPathFromUrl(context, url)
        var totalBytes: Long = 0

        outputFile.sink().buffer().use { sink ->
            body.source().use { source ->
                val buffer = Buffer()
                while (true) {
                    val read = source.read(buffer, 8_192)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    totalBytes += read

                    if (totalBytes % (1024 * 100) == 0L) {
                        val progress = if (fileSize > 0) {
                            (totalBytes * 100 / fileSize).toInt()
                        } else 0
                        showProgressNotification(index, total, "Downloading ${getFileNameFromUrl(url)} ($progress%)")
                    }
                }
                sink.flush()
            }
        }
        DownloadUtils.updateResourceOfflineStatus(url)
    }

    private suspend fun showProgressNotification(current: Int, total: Int, text: String) {
        val notification = DownloadUtils.buildProgressNotification(
            context,
            current,
            total,
            text,
            forWorker = true
        )
        setForeground(ForegroundInfo(WORKER_NOTIFICATION_ID, notification))
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
        val broadcastService = getBroadcastService(applicationContext)
        broadcastService.sendBroadcast(intent)
    }


    companion object {
        const val WORKER_NOTIFICATION_ID = 3
        const val COMPLETION_NOTIFICATION_ID = 4
    }
}
