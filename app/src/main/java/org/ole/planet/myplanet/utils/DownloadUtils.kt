package org.ole.planet.myplanet.utils

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.regex.Pattern
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.DownloadService
import org.ole.planet.myplanet.services.DownloadWorker

object DownloadUtils {
    private const val DOWNLOAD_CHANNEL = "DownloadChannel"
    private const val COMPLETION_CHANNEL = "DownloadCompletionChannel"
    private const val WORKER_CHANNEL = "DownloadWorkerChannel"

    @JvmStatic
    fun createChannels(context: Context) {
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
    }

    @JvmStatic
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
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @JvmStatic
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

        if (fileProgress in 0..100) {
            builder.setProgress(100, fileProgress, false)
            builder.setSubText("$current/$total files")
        } else {
            builder.setProgress(total, current, false)
        }

        return builder.build()
    }

    @JvmStatic
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
    @JvmStatic
    fun downloadAllFiles(dbMyLibrary: List<RealmMyLibrary?>): ArrayList<String> {
        return ArrayList(dbMyLibrary.map { UrlUtils.getUrl(it) })
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun openPriorityDownloadService(context: Context?, urls: ArrayList<String>) {
        context?.let { ctx ->
            val preferences = ctx.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)

            val existingPriority = preferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) ?: emptySet()
            val mergedPriority = existingPriority.toMutableSet().apply { addAll(urls) }

            preferences.edit {
                putStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, mergedPriority)
            }

            val serviceRunning = isDownloadServiceRunning(ctx)
            if (!serviceRunning) {
                startDownloadServiceSafely(ctx, DownloadService.PRIORITY_DOWNLOADS_KEY, false)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun openDownloadService(context: Context?, urls: ArrayList<String>, fromSync: Boolean) {
        context?.let { ctx ->
            val preferences = ctx.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)

            val existingUrls = preferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet()) ?: emptySet()
            val mergedUrls = existingUrls.toMutableSet().apply { addAll(urls) }

            preferences.edit {
                putStringSet(DownloadService.PENDING_DOWNLOADS_KEY, mergedUrls)
            }

            val serviceRunning = isDownloadServiceRunning(ctx)
            if (!serviceRunning) {
                startDownloadServiceSafely(ctx, DownloadService.PENDING_DOWNLOADS_KEY, fromSync)
            }
        }
    }

    private fun isDownloadServiceRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (DownloadService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startDownloadServiceSafely(context: Context, urlsKey: String, fromSync: Boolean) {
        if (canStartForegroundService(context)) {
            try {
                DownloadService.startService(context, urlsKey, fromSync)
            } catch (e: Exception) {
                e.printStackTrace()
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
            .addTag("download_work")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun canStartForegroundService(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> true
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
        val pattern = Pattern.compile("!\\[.*?]\\((.*?)\\)")
        val matcher = text?.let { pattern.matcher(it) }
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

    @JvmStatic
    fun updateResourceOfflineStatus(url: String) {
        MainApplication.applicationScope.launch {
            try {
                resourcesRepository.markResourceOfflineByUrl(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val resourcesRepository: ResourcesRepository by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(
            MainApplication.context,
            DownloadUtilsEntryPoint::class.java
        )
        entryPoint.resourcesRepository()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadUtilsEntryPoint {
        fun resourcesRepository(): ResourcesRepository
    }
}
