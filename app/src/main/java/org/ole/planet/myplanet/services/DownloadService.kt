package org.ole.planet.myplanet.services

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.DownloadPreferences
import org.ole.planet.myplanet.di.getBroadcastService
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.repository.DownloadRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.DownloadWorker
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.FileUtils.availableExternalMemorySize
import org.ole.planet.myplanet.utils.FileUtils.externalMemoryAvailable
import org.ole.planet.myplanet.utils.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utils.UrlUtils.header

@AndroidEntryPoint
class DownloadService : Service() {
    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var resourcesRepository: ResourcesRepository

    @Inject
    lateinit var serverUrlMapper: ServerUrlMapper

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    private var data = ByteArray(BUFFER_SIZE)
    private var outputFile: File? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var notificationManager: NotificationManager? = null
    private var totalFileSize = 0

    @Inject
    @DownloadPreferences
    lateinit var preferencesProvider: Provider<SharedPreferences>
    private val preferences: SharedPreferences by lazy { preferencesProvider.get() }

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    private var currentDownloadUrl: String = ""
    private var originalDownloadUrl: String = ""
    private var fromSync = false
    private var lastNotificationUpdateTime = 0L
    private var currentFileProgress = 0
    private val processedUrls = mutableSetOf<String>()
    private var sessionTotalCount = 0
    private var sessionCompletedCount = 0
    private var isCurrentDownloadPriority = false
    private var isQueueRunning = false

    private var currentJob: Job? = null
    private lateinit var broadcastService: BroadcastService

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        broadcastService = getBroadcastService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        DownloadUtils.createChannels(this)

        val initialNotification = DownloadUtils.buildInitialNotification(this)
        startForeground(ONGOING_NOTIFICATION_ID, initialNotification)

        fromSync = intent?.getBooleanExtra("fromSync", false) == true
        Log.d(TAG, "onStartCommand: fromSync=$fromSync queueRunning=$isQueueRunning")

        if (!isQueueRunning) {
            isQueueRunning = true
            currentJob?.cancel()
            currentJob = appScope.launch {
                try {
                    processDownloadQueue()
                } finally {
                    isQueueRunning = false
                }
            }
        } else {
            Log.d(TAG, "Queue already running, new URLs will be picked up by current loop")
        }

        return START_STICKY
    }

    private suspend fun processDownloadQueue() {
        Log.d(TAG, "processDownloadQueue: started")
        while (true) {
            val nextUrl = getNextPriorityUrl() ?: getNextPendingUrl()

            if (nextUrl == null) {
                Log.d(TAG, "processDownloadQueue: queue empty — completed=$sessionCompletedCount total=$sessionTotalCount")
                if (sessionCompletedCount > 0) {
                    showCompletionNotification(false)
                }
                stopSelf()
                return
            }

            processedUrls.add(nextUrl.url)
            sessionTotalCount++
            val remaining = getRemainingCount()
            Log.d(TAG, "processDownloadQueue: [${sessionTotalCount}] ${nextUrl.url.substringAfterLast('/')} priority=${nextUrl.isPriority} remaining=$remaining")

            isCurrentDownloadPriority = nextUrl.isPriority
            updateNotificationForBatchDownload()
            val succeeded = initDownload(nextUrl.url, fromSync)
            Log.d(TAG, "processDownloadQueue: [${sessionTotalCount}] succeeded=$succeeded completed=$sessionCompletedCount")

            if (succeeded) sessionCompletedCount++

            cleanupProcessedUrls()
        }
    }

    internal data class QueuedUrl(val url: String, val isPriority: Boolean, val priority: Int = 0)

    internal fun getNextPriorityUrl(): QueuedUrl? {
        return Companion.getNextUrl(preferences, PRIORITY_DOWNLOADS_KEY, processedUrls, true)
    }

    internal fun getNextPendingUrl(): QueuedUrl? {
        return Companion.getNextUrl(preferences, PENDING_DOWNLOADS_KEY, processedUrls, false)
    }

    private fun getRemainingCount(priorityUrls: Set<String>? = null): Int {
        val priority = priorityUrls ?: preferences.getStringSet(PRIORITY_DOWNLOADS_KEY, emptySet()) ?: emptySet()
        val pendingUrls = preferences.getStringSet(PENDING_DOWNLOADS_KEY, emptySet()) ?: emptySet()
        val allUrls = priority + pendingUrls
        return allUrls.count { it !in processedUrls }
    }

    private fun cleanupProcessedUrls() {
        val remainingPriority = preferences.getStringSet(PRIORITY_DOWNLOADS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        remainingPriority.removeAll(processedUrls)
        val remainingPending = preferences.getStringSet(PENDING_DOWNLOADS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        remainingPending.removeAll(processedUrls)
        preferences.edit {
            putStringSet(PRIORITY_DOWNLOADS_KEY, remainingPriority)
            putStringSet(PENDING_DOWNLOADS_KEY, remainingPending)
        }
    }

    private fun updateNotificationForBatchDownload() {
        DownloadUtils.createChannels(this)
        notificationBuilder = NotificationCompat.Builder(this, "DownloadChannel")
            .setContentTitle(getString(R.string.downloading_files))
            .setContentText("Starting downloads (0/${getRemainingCount() + 1})")
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)

        notificationManager?.notify(ONGOING_NOTIFICATION_ID, notificationBuilder?.build())
    }

    private suspend fun initDownload(url: String, fromSync: Boolean): Boolean {
        currentDownloadUrl = url
        originalDownloadUrl = url
        val fileName = url.substringAfterLast('/')
        Log.d(TAG, "initDownload: $fileName fromSync=$fromSync")
        try {
            if (url.isBlank()) {
                Log.e(TAG, "initDownload: blank URL, skipping")
                downloadFailed("Invalid URL - empty or blank", fromSync)
                return false
            }

            if (FileUtils.checkFileExist(this, url)) {
                Log.d(TAG, "initDownload: $fileName already on disk, marking offline and skipping download")
                try {
                    resourcesRepository.markResourceOfflineByUrl(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                onDownloadComplete(url)
                return true
            }

            val authHeader = header
            if (authHeader.isBlank()) {
                Log.e(TAG, "initDownload: auth header is blank — user may not be logged in")
                downloadFailed("Authentication header not available", fromSync)
                return false
            }

            Log.d(TAG, "initDownload: fetching $fileName from primary URL")
            val primaryResult = downloadRepository.downloadFileResponse(url, authHeader)

            if (primaryResult is DownloadResult.Error && primaryResult.code == null) {
                Log.w(TAG, "initDownload: primary failed with network error (${primaryResult.message}), checking for alternative URL")
                val altUrl = resolveAlternativeUrl(url, fileName)
                if (altUrl != null) {
                    Log.d(TAG, "initDownload: retrying with $altUrl")
                    currentDownloadUrl = altUrl
                    val altResult = downloadRepository.downloadFileResponse(altUrl, authHeader)
                    return tryDownloadFromResult(altResult, altUrl, fromSync, fileName, isAlternative = true)
                }
            }

            return tryDownloadFromResult(primaryResult, url, fromSync, fileName, isAlternative = false)
        } catch (e: Exception) {
            Log.e(TAG, "initDownload: unexpected error for $fileName", e)
            downloadFailed("Download initialization failed: ${e.localizedMessage ?: "Unknown error"}", fromSync)
            return false
        }
    }

    private fun resolveAlternativeUrl(url: String, fileName: String): String? {
        val mapping = serverUrlMapper.processUrl(url)
        val altBase = mapping.alternativeUrl
        val primaryBase = mapping.extractedBaseUrl

        val resolvedAltBase: String?
        val resolvedPrimaryBase: String?
        if (altBase != null && primaryBase != null) {
            resolvedAltBase = altBase
            resolvedPrimaryBase = primaryBase
            Log.d(TAG, "initDownload: found hardcoded mapping $primaryBase → $altBase")
        } else {
            val storedAlt = sharedPrefManager.getProcessedAlternativeUrl()
            if (storedAlt.isNotEmpty() && primaryBase != null) {
                resolvedAltBase = storedAlt.trimEnd('/')
                resolvedPrimaryBase = primaryBase
                Log.d(TAG, "initDownload: no hardcoded mapping for $primaryBase — using stored alternative $resolvedAltBase")
            } else {
                resolvedAltBase = null
                resolvedPrimaryBase = null
                Log.w(TAG, "initDownload: no alternative URL available for primary base '$primaryBase', giving up")
            }
        }

        if (resolvedAltBase != null && resolvedPrimaryBase != null) {
            val parsed = Uri.parse(url)
            val path = parsed.path.orEmpty()
            val query = if (parsed.query != null) "?${parsed.query}" else ""
            val altUrl = resolvedAltBase + path + query
            Log.d(TAG, "initDownload: switching $fileName — primary=$resolvedPrimaryBase → alternative=$resolvedAltBase")
            return altUrl
        }
        return null
    }

    private suspend fun tryDownloadFromResult(
        result: DownloadResult,
        url: String,
        fromSync: Boolean,
        fileName: String,
        isAlternative: Boolean
    ): Boolean {
        val source = if (isAlternative) "alternative" else "primary"

        if (result is DownloadResult.Error) {
            Log.e(TAG, "tryDownload [$source]: $fileName — ${result.message} (code=${result.code})")
            downloadFailed(result.message, fromSync)
            return false
        }

        result as DownloadResult.Success
        val contentLength = result.body.contentLength()
        Log.d(TAG, "tryDownload [$source]: $fileName responded contentLength=${if (contentLength == -1L) "unknown" else "${contentLength}B"}")

        val storageError = getStorageError(contentLength)
        if (storageError != null) {
            Log.e(TAG, "tryDownload [$source]: storage check failed — $storageError")
            downloadFailed(storageError, fromSync)
            return false
        }

        if (contentLength == 0L) {
            Log.e(TAG, "tryDownload [$source]: server returned empty body for $fileName")
            downloadFailed("Empty file from server", fromSync)
            return false
        }

        return try {
            downloadFile(result.body, url)
            true
        } catch (e: Exception) {
            Log.e(TAG, "tryDownload [$source]: write failed for $fileName", e)
            downloadFailed(e.localizedMessage ?: "Write failed", fromSync)
            false
        }
    }

    private fun downloadFailed(message: String, fromSync: Boolean) {
        val remaining = getRemainingCount()
        notificationBuilder?.apply {
            setContentText("Error: $message")
            setSubText("$sessionCompletedCount completed, $remaining remaining")
            notificationManager?.notify(ONGOING_NOTIFICATION_ID, build())
        }

        val download = Download().apply {
            failed = true
            this.message = message
        }
        sendIntent(download, fromSync)

        if (!fromSync) {
            if (message == "File Not Found") {
                val intent = Intent(RESOURCE_NOT_FOUND_ACTION)
                appScope.launch {
                    broadcastService.sendBroadcast(intent)
                }
            }
        }
    }

    @Throws(IOException::class)
    private suspend fun downloadFile(body: ResponseBody, url: String) {
        val fileSize = body.contentLength()
        val finalFile = FileUtils.getSDPathFromUrl(this@DownloadService, url)
        finalFile.parentFile?.mkdirs()
        val tempFile = File(finalFile.parentFile, "${finalFile.name}.tmp")
        tempFile.delete()
        outputFile = finalFile
        var total: Long = 0
        val fileName = url.substringAfterLast('/')
        Log.d(TAG, "downloadFile: writing $fileName to ${tempFile.absolutePath} size=${if (fileSize == -1L) "unknown" else "${fileSize}B"}")

        try {
            BufferedInputStream(body.byteStream(), 1024 * 8).use { bis ->
                FileOutputStream(tempFile).use { output ->
                    val download = Download().apply {
                        this.fileName = getFileNameFromUrl(url)
                    }

                    if (fileSize > 0) {
                        totalFileSize = (fileSize / 1024.0).toInt()
                        download.totalFileSize = totalFileSize
                    } else {
                        download.totalFileSize = 0
                        download.progress = -1
                        currentFileProgress = -1
                    }

                    total = copyStreamWithProgress(bis, output, download, fileSize, total)
                }
            }
            if (!tempFile.renameTo(finalFile)) {
                Log.d(TAG, "downloadFile: rename failed for $fileName, falling back to copy")
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            Log.d(TAG, "downloadFile: complete — $fileName written ${total}B to ${finalFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "downloadFile: failed for $fileName after ${total}B, temp file deleted", e)
            tempFile.delete()
            throw e
        }
        onDownloadComplete(url)
    }

    private fun copyStreamWithProgress(
        bis: BufferedInputStream,
        output: FileOutputStream,
        download: Download,
        fileSize: Long,
        initialTotal: Long
    ): Long {
        var total = initialTotal
        while (true) {
            val readCount = bis.read(data)
            if (readCount == -1) break

            if (readCount > 0) {
                total += readCount

                if (fileSize > 0) {
                    val progress = (total * 100 / fileSize).toInt()
                    download.progress = progress
                    currentFileProgress = progress
                }

                val now = SystemClock.elapsedRealtime()
                if (now - lastNotificationUpdateTime >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                    val current = (total / 1024.0).roundToInt()
                    download.currentFileSize = current
                    sendNotification(download)
                    lastNotificationUpdateTime = now
                }
                output.write(data, 0, readCount)
            }
        }
        return total
    }

    private fun getStorageError(fileSize: Long): String? {
        if (!externalMemoryAvailable()) return "Download failed: storage not available"
        val required = if (fileSize > 0) fileSize + STORAGE_HEADROOM_BYTES else STORAGE_HEADROOM_BYTES
        if (required > availableExternalMemorySize) {
            Log.e(TAG, "getStorageError: need ${required}B (file=${fileSize}B + headroom) but only ${availableExternalMemorySize}B available")
            return "Download failed: not enough storage"
        }
        return null
    }

    private fun sendNotification(download: Download) {
        val url = currentDownloadUrl
        if (url.isBlank()) return

        val fileName = getFileNameFromUrl(url)
        download.fileName = "Downloading: $fileName"
        download.fileUrl = originalDownloadUrl.ifEmpty { url }
        sendIntent(download, fromSync)

        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            notificationBuilder?.apply {
                val remaining = getRemainingCount()
                val progressText = if (currentFileProgress in 0..100) {
                    "$fileName ($currentFileProgress%)"
                } else {
                    fileName
                }

                if (currentFileProgress in 0..100) {
                    setProgress(100, currentFileProgress, false)
                    setSubText("$sessionCompletedCount completed, $remaining remaining")
                } else {
                    setProgress(100, 0, true)
                    setSubText("$sessionCompletedCount completed, $remaining remaining")
                }
                setContentText(progressText)
                notificationManager?.notify(ONGOING_NOTIFICATION_ID, build())
            }
        }
    }

    private fun sendIntent(download: Download, fromSync: Boolean) {
        val intent = Intent(MESSAGE_PROGRESS).apply {
            putExtra("download", download)
            putExtra("fromSync", fromSync)
        }
        appScope.launch {
            broadcastService.sendBroadcast(intent)
        }
    }

    private suspend fun onDownloadComplete(url: String) {
        if ((outputFile?.length() ?: 0) > 0) {
            try {
                resourcesRepository.markResourceOfflineByUrl(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val priorityUrls = preferences.getStringSet(PRIORITY_DOWNLOADS_KEY, emptySet()) ?: emptySet()
        val remainingPriority = priorityUrls.count { it !in processedUrls }
        val remaining = getRemainingCount(priorityUrls)

        val fileName = getFileNameFromUrl(url)
        val download = Download().apply {
            this.fileName = fileName
            fileUrl = originalDownloadUrl.ifEmpty { url }
            progress = 100
            completeAll = (remaining == 0) || (isCurrentDownloadPriority && remainingPriority == 0)
        }

        sendIntent(download, fromSync)
        notificationBuilder?.apply {
            setProgress(sessionCompletedCount + remaining, sessionCompletedCount, false)
            setContentText("Downloaded $fileName")
            setSubText("$sessionCompletedCount completed, $remaining remaining")
            notificationManager?.notify(ONGOING_NOTIFICATION_ID, build())
        }
    }

    private fun showCompletionNotification(hadErrors: Boolean) {
        val notification = DownloadUtils.buildCompletionNotification(
            this,
            sessionCompletedCount,
            sessionTotalCount,
            hadErrors,
            forWorker = false
        )

        notificationManager?.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        try {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground service", e)
        }
        currentJob?.cancel()
        notificationManager?.cancel(ONGOING_NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val STORAGE_HEADROOM_BYTES = 100L * 1024 * 1024
        private const val BUFFER_SIZE = 1024 * 16
        const val PREFS_NAME = "MyPrefsFile"
        const val MESSAGE_PROGRESS = "message_progress"
        const val RESOURCE_NOT_FOUND_ACTION = "resource_not_found_action"
        const val ONGOING_NOTIFICATION_ID = 1
        const val COMPLETION_NOTIFICATION_ID = 2
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L
        const val PENDING_DOWNLOADS_KEY = "pending_downloads_queue"
        const val PRIORITY_DOWNLOADS_KEY = "priority_downloads_queue"

        internal fun getNextPriorityUrl(downloadQueue: List<QueuedUrl>): QueuedUrl? {
            if (downloadQueue.isEmpty()) return null
            return downloadQueue.maxByOrNull { it.priority } ?: downloadQueue.first()
        }

        @VisibleForTesting
        internal fun getNextUrl(
            preferences: SharedPreferences,
            key: String,
            processedUrls: Set<String>,
            isPriority: Boolean
        ): QueuedUrl? {
            val urls = preferences.getStringSet(key, emptySet()) ?: emptySet()
            return urls
                .filter { it !in processedUrls && it.isNotBlank() }
                .minOrNull()
                ?.let { QueuedUrl(it, isPriority) }
        }

        fun startService(context: Context, urlsKey: String, fromSync: Boolean) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra("urls_key", urlsKey)
                putExtra("fromSync", fromSync)
            }

            val canStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context is Activity || hasValidForegroundServiceContext(context)
            } else {
                true
            }

            if (canStart) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start foreground service", e)
                    handleForegroundServiceError(context, urlsKey, fromSync)
                }
            } else {
                startDownloadWork(context, urlsKey, fromSync)
            }
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun hasValidForegroundServiceContext(context: Context): Boolean {
            val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            return activityManager.isBackgroundRestricted.not()
        }

        private fun handleForegroundServiceError(context: Context, urlsKey: String, fromSync: Boolean) {
            try {
                val intent = Intent(context, DownloadService::class.java).apply {
                    putExtra("urls_key", urlsKey)
                    putExtra("fromSync", fromSync)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
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
