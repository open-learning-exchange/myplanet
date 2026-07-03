package org.ole.planet.myplanet.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.Buffer
import okio.buffer
import okio.sink
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.di.DownloadPreferences
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.repository.DownloadRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utils.UrlUtils

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context, @Assisted workerParams: WorkerParameters,
    private val downloadRepository: DownloadRepository, private val broadcastService: BroadcastService,
    private val dispatcherProvider: DispatcherProvider,
    @DownloadPreferences private val preferences: SharedPreferences
) : CoroutineWorker(context, workerParams) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(dispatcherProvider.io) {
        try {
            val urlsKey = inputData.getString("urls_key") ?: "url_list_key"
            val fromSync = inputData.getBoolean("fromSync", false)
            val urlSet = preferences.getStringSet(urlsKey, emptySet()) ?: emptySet()

            if (urlSet.isEmpty()) {
                return@withContext Result.failure()
            }

            val urls = urlSet.toTypedArray()
            DownloadUtils.createChannels(context)

            showProgressNotification(0, urls.size, context.getString(R.string.starting_downloads), -1)

            var completedCount = 0
            val results = mutableListOf<Boolean>()

            urls.forEachIndexed { index, url ->
                try {
                    val success = downloadFile(url, index, urls.size)
                    results.add(success)
                    completedCount++

                    showProgressNotification(completedCount - 1, urls.size, context.getString(R.string.downloaded_files, "$completedCount", "${urls.size}"), 100)
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
        if (FileUtils.checkFileExist(context, url)) {
            DownloadUtils.updateResourceOfflineStatus(url)
            return true
        }
        return try {
            val response = downloadRepository.downloadFileResponse(url, UrlUtils.header)
            when (response) {
                is DownloadResult.Success -> {
                    downloadFileBody(response.body, url, index, total)
                    true
                }
                is DownloadResult.Error -> {
                    false
                }
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
        var lastUpdateTime = 0L

        outputFile.sink().buffer().use { sink ->
            body.source().use { source ->
                val buffer = Buffer()
                while (true) {
                    val read = source.read(buffer, 8_192)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    totalBytes += read

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUpdateTime >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                        val progress = if (fileSize > 0) {
                            (totalBytes * 100 / fileSize).toInt()
                        } else -1
                        showProgressNotification(index, total, getFileNameFromUrl(url), progress)
                        lastUpdateTime = now
                    }
                }
                sink.flush()
            }
        }
        DownloadUtils.updateResourceOfflineStatus(url)
    }

    private suspend fun showProgressNotification(current: Int, total: Int, fileName: String, fileProgress: Int = -1) {
        val text = if (fileProgress in 0..100) {
            "$fileName ($fileProgress%)"
        } else {
            fileName
        }
        val notification = DownloadUtils.buildProgressNotification(
            context, current + 1, total, text, forWorker = true, fileProgress = fileProgress
        )
        setForeground(ForegroundInfo(WORKER_NOTIFICATION_ID, notification))
    }

    private fun showCompletionNotification(completed: Int, total: Int, hadErrors: Boolean) {
        val notification = DownloadUtils.buildCompletionNotification(
            context, completed, total, hadErrors, forWorker = true
        )

        notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    private suspend fun sendDownloadUpdate(url: String, success: Boolean, isComplete: Boolean, fromSync: Boolean) {
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

        val intent = Intent(DownloadService.MESSAGE_PROGRESS).apply {
            putExtra("download", download)
            putExtra("fromSync", fromSync)
        }
        broadcastService.sendBroadcast(intent)
    }

    companion object {
        const val WORKER_NOTIFICATION_ID = 3
        const val COMPLETION_NOTIFICATION_ID = 4
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L
    }
}
