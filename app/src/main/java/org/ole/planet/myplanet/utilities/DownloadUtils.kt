package org.ole.planet.myplanet.utilities

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import io.realm.Realm
import java.util.regex.Pattern
import kotlin.text.isNotEmpty
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.FileUtils

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
    @JvmStatic
    fun downloadAllFiles(dbMyLibrary: List<RealmMyLibrary?>): ArrayList<String> {
        val urls = ArrayList<String>()
        for (i in dbMyLibrary.indices) {
            urls.add(Utilities.getUrl(dbMyLibrary[i]))
        }
        return urls
    }

    @JvmStatic
    fun downloadFiles(dbMyLibrary: List<RealmMyLibrary?>, selectedItems: ArrayList<Int>): ArrayList<String> {
        val urls = ArrayList<String>()
        for (i in selectedItems.indices) {
            urls.add(Utilities.getUrl(dbMyLibrary[selectedItems[i]]))
        }
        return urls
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
        try {
            val backgroundRealm = Realm.getDefaultInstance()
            backgroundRealm.use { realm ->
                realm.executeTransactionAsync {
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
}
