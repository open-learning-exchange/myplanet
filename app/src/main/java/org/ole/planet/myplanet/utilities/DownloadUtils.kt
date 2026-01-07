package org.ole.planet.myplanet.utilities

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
import org.ole.planet.myplanet.service.DownloadWorker
import org.ole.planet.myplanet.service.DownloadService

object DownloadUtils {
    private const val DOWNLOAD_CHANNEL = "DownloadChannel"
    private const val COMPLETION_CHANNEL = "DownloadCompletionChannel"
    private const val WORKER_CHANNEL = "DownloadWorkerChannel"

    @JvmStatic
    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(DOWNLOAD_CHANNEL) == null) {
                val channel = NotificationChannel(
                    DOWNLOAD_CHANNEL,
                    "Download Service",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    description = "Shows download progress for files"
                }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(COMPLETION_CHANNEL) == null) {
                val channel = NotificationChannel(
                    COMPLETION_CHANNEL,
                    "Download Completion",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies when downloads are completed"
                }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(WORKER_CHANNEL) == null) {
                val channel = NotificationChannel(
                    WORKER_CHANNEL,
                    "Background Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress for background downloads"
                    setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @JvmStatic
    fun buildProgressNotification(
        context: Context,
        current: Int,
        total: Int,
        text: String,
        forWorker: Boolean = false
    ): Notification {
        val channel = if (forWorker) WORKER_CHANNEL else DOWNLOAD_CHANNEL
        createChannels(context)
        return NotificationCompat.Builder(context, channel)
            .setContentTitle(context.getString(R.string.downloading_files))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(total, current, false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(!forWorker)
            .setSilent(true)
            .build()
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

    @JvmStatic
    fun downloadFiles(
        dbMyLibrary: List<RealmMyLibrary?>,
        selectedItems: ArrayList<Int>
    ): ArrayList<String> {
        return ArrayList(selectedItems.map { UrlUtils.getUrl(dbMyLibrary[it]) })
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun openDownloadService(context: Context?, urls: ArrayList<String>, fromSync: Boolean) {
        context?.let { ctx ->
            val preferences = ctx.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
            preferences.edit {
                putStringSet("url_list_key", urls.toSet())
            }
            startDownloadServiceSafely(ctx, "url_list_key", fromSync)
        }
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
        val currentFileName = FileUtils.getFileNameFromUrl(url)
        if (currentFileName.isBlank()) {
            return
        }

        MainApplication.applicationScope.launch {
            try {
                resourcesRepository.markResourceOfflineByLocalAddress(currentFileName)
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
