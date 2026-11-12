package org.ole.planet.myplanet.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.Buffer
import okio.appendingSink
import okio.buffer
import okio.sink
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.di.DownloadPreferences
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.repository.DownloadRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
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
    private val resourcesRepository: ResourcesRepository,
    @DownloadPreferences private val preferences: SharedPreferences
) : CoroutineWorker(context, workerParams) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var isForegroundPromoted = false

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

            val authHeader = UrlUtils.header

            urls.forEachIndexed { index, url ->
                val success = try {
                    downloadFile(url, authHeader, index, urls.size)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download ${getFileNameFromUrl(url)}", e)
                    false
                }
                results.add(success)
                completedCount++

                try {
                    showProgressNotification(completedCount - 1, urls.size, context.getString(R.string.downloaded_files, "$completedCount", "${urls.size}"), 100)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update progress notification for ${getFileNameFromUrl(url)}", e)
                }
                try {
                    sendDownloadUpdate(url, success, completedCount >= urls.size, fromSync)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send download update for ${getFileNameFromUrl(url)}", e)
                }
            }

            showCompletionNotification(completedCount, urls.size, results.any { !it })
            removeCompletedUrls(urlsKey, urls.filterIndexed { i, _ -> results[i] }.toSet())
            resultFor(succeeded = results.count { it }, attempted = results.size, runAttemptCount = runAttemptCount)
        } catch (e: CancellationException) {
            notificationManager.cancel(WORKER_NOTIFICATION_ID)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download worker failed", e)
            Result.failure()
        }
    }

    private fun removeCompletedUrls(urlsKey: String, completed: Set<String>) {
        if (completed.isEmpty()) return
        val remaining = preferences.getStringSet(urlsKey, emptySet())?.toMutableSet() ?: return
        remaining.removeAll(completed)
        preferences.edit { putStringSet(urlsKey, remaining) }
    }

    private suspend fun downloadFile(url: String, authHeader: String, index: Int, total: Int): Boolean {
        if (FileUtils.checkFileExist(context, url)) {
            try {
                resourcesRepository.markResourceOfflineByUrl(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark existing resource offline: ${UrlUtils.redactForLog(url)}", e)
            }
            return true
        }
        return try {
            val outputFile = FileUtils.getSDPathFromUrl(context, url)
            outputFile.parentFile?.mkdirs()
            val partial = PartialFiles(outputFile)
            File(outputFile.parentFile, "${outputFile.name}.part").delete()

            val resumeOffset = partial.resumeOffset()
            val ifRange = if (resumeOffset > 0) partial.validator() else null
            var response = downloadRepository.downloadFileResponse(url, authHeader, resumeOffset, ifRange)
            if (response is DownloadResult.Error && response.code == 416 && resumeOffset > 0) {
                Log.w(TAG, "Resume offset ${resumeOffset}B rejected (416) for ${getFileNameFromUrl(url)}, restarting")
                partial.discard()
                response = downloadRepository.downloadFileResponse(url, authHeader, 0L, null)
            }
            when (response) {
                is DownloadResult.Success -> {
                    downloadFileBody(response, url, partial, index, total)
                    true
                }
                is DownloadResult.Error -> {
                    Log.e(TAG, "Failed to download file: ${getFileNameFromUrl(url)} (code=${response.code}) ${response.message}")
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download file: ${UrlUtils.redactForLog(url)}", e)
            false
        }
    }

    private class PartialFiles(val outputFile: File) {
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        private val validatorFile = File(outputFile.parentFile, "${outputFile.name}.tmp.etag")
        private val sizeFile = File(outputFile.parentFile, "${outputFile.name}.tmp.size")

        fun resumeOffset(): Long = if (tempFile.exists()) tempFile.length() else 0L

        fun validator(): String? =
            if (validatorFile.exists()) validatorFile.readText().takeIf { it.isNotBlank() } else null

        fun writeValidator(validator: String?) {
            if (validator.isNullOrBlank()) validatorFile.delete() else validatorFile.writeText(validator)
        }

        fun writeExpectedSize(size: Long) {
            if (size > 0) sizeFile.writeText(size.toString()) else sizeFile.delete()
        }

        fun discard() {
            tempFile.delete()
            clearSidecars()
        }

        fun clearSidecars() {
            validatorFile.delete()
            sizeFile.delete()
        }
    }

    private suspend fun downloadFileBody(response: DownloadResult.Success, url: String, partial: PartialFiles, index: Int, total: Int) {
        val body = response.body
        val isPartial = response.code == 206
        if (!isPartial) partial.tempFile.delete()
        val startOffset = if (isPartial) partial.tempFile.length() else 0L
        partial.writeValidator(response.validator)

        val remaining = body.contentLength()
        val expectedSize = if (remaining > 0) startOffset + remaining else -1L
        partial.writeExpectedSize(expectedSize)

        val totalBytes = writeBody(body, partial.tempFile, isPartial, startOffset, expectedSize, url, index, total)
        if (expectedSize > 0 && totalBytes != expectedSize) {
            throw IOException("Incomplete download: $totalBytes of $expectedSize bytes")
        }
        val outputFile = partial.outputFile
        if (!partial.tempFile.renameTo(outputFile)) {
            partial.tempFile.copyTo(outputFile, overwrite = true)
            partial.tempFile.delete()
        }
        partial.clearSidecars()
        try {
            resourcesRepository.markResourceOfflineByUrl(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark downloaded resource offline: ${UrlUtils.redactForLog(url)}", e)
        }
    }

    private suspend fun writeBody(
        body: ResponseBody, tempFile: File, append: Boolean, startOffset: Long, fileSize: Long, url: String, index: Int, total: Int
    ): Long {
        var totalBytes: Long = startOffset
        var lastUpdateTime = 0L
        val coroutineContext = currentCoroutineContext()

        (if (append) tempFile.appendingSink() else tempFile.sink()).buffer().use { sink ->
            body.source().use { source ->
                val buffer = Buffer()
                while (true) {
                    coroutineContext.ensureActive()
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
        return totalBytes
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
        if (isForegroundPromoted || DownloadUtils.canStartForegroundService(context)) {
            try {
                setForeground(
                    ForegroundInfo(WORKER_NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                )
                isForegroundPromoted = true
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to promote download worker to foreground, showing plain notification", e)
            }
        }
        notificationManager.notify(WORKER_NOTIFICATION_ID, notification)
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
        private const val TAG = "DownloadWorker"
        const val WORK_TAG = "download_work"
        internal const val MAX_RETRY_ATTEMPTS = 3

        internal fun resultFor(succeeded: Int, attempted: Int, runAttemptCount: Int): Result = when {
            attempted == 0 || succeeded > 0 -> Result.success()
            runAttemptCount < MAX_RETRY_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
        const val WORKER_NOTIFICATION_ID = 3
        const val COMPLETION_NOTIFICATION_ID = 4
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L
    }
}
