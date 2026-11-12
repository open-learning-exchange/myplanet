package org.ole.planet.myplanet.utils

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.regex.Pattern
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.services.DownloadService
import org.ole.planet.myplanet.services.DownloadStopReceiver
import org.ole.planet.myplanet.services.DownloadWorker

object DownloadUtils {
    private const val TAG = "DownloadUtils"
    private const val DOWNLOAD_CHANNEL = "DownloadChannel"
    private const val COMPLETION_CHANNEL = "DownloadCompletionChannel"
    private const val WORKER_CHANNEL = "DownloadWorkerChannel"
    private val LINK_PATTERN = Pattern.compile("!\\[.*?]\\((.*?)\\)")

    @Volatile
    private var channelsCreated = false

    fun createChannels(context: Context) {
        if (channelsCreated) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(DOWNLOAD_CHANNEL) == null) {
            val channel = NotificationChannel(DOWNLOAD_CHANNEL, "Download Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                description = "Shows download progress for files"
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(COMPLETION_CHANNEL) == null) {
            val channel = NotificationChannel(COMPLETION_CHANNEL, "Download Completion",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when downloads are completed"
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(WORKER_CHANNEL) == null) {
            val channel = NotificationChannel(WORKER_CHANNEL, "Background Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for background downloads"
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
        channelsCreated = true
    }

    @VisibleForTesting
    internal fun resetChannelsCreatedForTesting() {
        channelsCreated = false
    }

    fun buildInitialNotification(context: Context): Notification {
        createChannels(context)
        return NotificationCompat.Builder(context, DOWNLOAD_CHANNEL)
            .setContentTitle(context.getString(R.string.downloading_files))
            .setContentText(context.getString(R.string.preparing_download))
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(DownloadStopReceiver.stopAction(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun buildProgressNotification(
        context: Context,
        current: Int,
        total: Int,
        text: String,
        forWorker: Boolean = false,
        fileProgress: Int = -1
    ): Notification {
        val channel = if (forWorker) WORKER_CHANNEL else DOWNLOAD_CHANNEL
        createChannels(context)
        val builder = NotificationCompat.Builder(context, channel)
            .setContentTitle(context.getString(R.string.downloading_files))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(!forWorker)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(DownloadStopReceiver.stopAction(context))

        if (fileProgress in 0..100) {
            builder.setProgress(100, fileProgress, false)
            builder.setSubText("$current/$total files")
        } else {
            builder.setProgress(total, current, false)
        }

        return builder.build()
    }

    fun buildCompletionNotification(
        context: Context,
        completed: Int,
        total: Int,
        hadErrors: Boolean,
        forWorker: Boolean = false
    ): Notification {
        val channel = if (forWorker) WORKER_CHANNEL else COMPLETION_CHANNEL
        createChannels(context)
        val text = if (hadErrors) {
            val errorMessage = context.getString(R.string.download_progress_with_errors, completed, total)
            errorMessage
        } else {
            context.getString(R.string.download_progress, completed, total)
        }
        return NotificationCompat.Builder(context, channel)
            .setContentTitle(context.getString(R.string.downloads_completed))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_download)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }
    fun downloadAllFiles(dbMyLibrary: List<MyLibrary?>): ArrayList<String> {
        val base = UrlUtils.getUrl()
        return dbMyLibrary.mapTo(ArrayList()) { library ->
            UrlUtils.getUrl(library?.resourceId, library?.resourceLocalAddress, base)
        }
    }

    fun queuedDownloadUrls(context: Context): Set<String> {
        val preferences = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        val priority = preferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) ?: emptySet()
        val pending = preferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet()) ?: emptySet()
        return priority + pending
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun openPriorityDownloadService(context: Context?, urls: ArrayList<String>) {
        context?.let { enqueueUrls(it, DownloadService.PRIORITY_DOWNLOADS_KEY, urls, false) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun openDownloadService(context: Context?, urls: ArrayList<String>, fromSync: Boolean) {
        context?.let { enqueueUrls(it, DownloadService.PENDING_DOWNLOADS_KEY, urls, fromSync) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun enqueueUrls(context: Context, key: String, urls: Collection<String>, fromSync: Boolean) {
        val preferences = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        val existing = preferences.getStringSet(key, emptySet()) ?: emptySet()
        val merged = existing.toMutableSet().apply { addAll(urls) }
        val syncOwned = if (fromSync) {
            (preferences.getStringSet(DownloadService.SYNC_DOWNLOADS_KEY, emptySet()) ?: emptySet()) + urls
        } else {
            null
        }
        preferences.edit {
            putStringSet(key, merged)
            syncOwned?.let { putStringSet(DownloadService.SYNC_DOWNLOADS_KEY, it) }
        }
        startDownloadServiceSafely(context, key, fromSync)
    }

    /**
     * The one Stop path, shared by the progress dialog and the notification action so the two can't drift.
     * Drops user-queued URLs (sync-owned ones stay queued), then halts whatever is in flight — the service
     * and every download worker, sync-owned included — the same way the service's own onDestroy stops
     * mid-file whatever it was fetching. Sync's queued URLs survive, so the next sync picks them back up.
     */
    fun stopDownloads(context: Context) {
        dropUserQueuedDownloads(context)
        context.stopService(Intent(context, DownloadService::class.java))
        cancelDownloadWork(context)
    }

    fun dropUserQueuedDownloads(context: Context) {
        val preferences = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        val syncOwned = preferences.getStringSet(DownloadService.SYNC_DOWNLOADS_KEY, emptySet()) ?: emptySet()
        val priority = preferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) ?: emptySet()
        val pending = preferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet()) ?: emptySet()
        preferences.edit(commit = true) {
            putStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, priority.intersect(syncOwned))
            putStringSet(DownloadService.PENDING_DOWNLOADS_KEY, pending.intersect(syncOwned))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startDownloadServiceSafely(context: Context, urlsKey: String, fromSync: Boolean) {
        if (canStartForegroundService(context)) {
            try {
                DownloadService.startService(context, urlsKey, fromSync)
            } catch (e: Exception) {
                Log.w(TAG, "startDownloadServiceSafely failed", e)
                handleForegroundServiceNotAllowed(context, urlsKey, fromSync)
            }
        } else {
            handleForegroundServiceNotAllowed(context, urlsKey, fromSync)
        }
    }

    private fun handleForegroundServiceNotAllowed(context: Context, urlsKey: String, fromSync: Boolean) {
        if (!fromSync) {
            Utilities.toast(context, context.getString(R.string.download_in_background))
        }
        startDownloadWork(context, urlsKey, fromSync)
    }

    private fun startDownloadWork(context: Context, urlsKey: String, fromSync: Boolean) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    "urls_key" to urlsKey,
                    "fromSync" to fromSync
                )
            )
            .addTag(DownloadWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun cancelDownloadWork(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(DownloadWorker.WORK_TAG)
    }

    fun canStartForegroundService(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                isAppInForeground(context)
            }
            else -> {
                isAppInForeground(context) || hasSpecialForegroundPermissions(context)
            }
        }
    }

    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = context.packageName
        return appProcesses.any { processInfo ->
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                processInfo.processName == packageName
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasSpecialForegroundPermissions(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                alarmManager.canScheduleExactAlarms()
            }
            else -> false
        }
    }

    fun extractLinks(text: String?): ArrayList<String> {
        val links = ArrayList<String>()
        val matcher = text?.let { LINK_PATTERN.matcher(it) }
        if (matcher != null) {
            while (matcher.find()) {
                val link = matcher.group(1)
                if (link != null) {
                    if (link.isNotEmpty()) {
                        links.add(link)
                    }
                }
            }
        }
        return links
    }
}
