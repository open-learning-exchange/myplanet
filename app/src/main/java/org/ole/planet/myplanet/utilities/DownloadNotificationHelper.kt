package org.ole.planet.myplanet.utilities

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import org.ole.planet.myplanet.R

object DownloadNotificationHelper {
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
                ).apply { setSound(null, null); description = "Shows download progress for files" }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(COMPLETION_CHANNEL) == null) {
                val channel = NotificationChannel(
                    COMPLETION_CHANNEL,
                    "Download Completion",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Notifies when downloads are completed" }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(WORKER_CHANNEL) == null) {
                val channel = NotificationChannel(
                    WORKER_CHANNEL,
                    "Background Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Shows progress for background downloads"; setSound(null, null) }
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
            context.getString(R.string.download_progress_with_errors, completed, total)
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
}

