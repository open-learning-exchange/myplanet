package org.ole.planet.myplanet.utilities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationUtil {
    private const val CHANNEL_ID = "ole_sync_channel"
    private const val NOTIFICATION_ID = 111

    @JvmStatic
    @Synchronized
    fun create(context: Context, smallIcon: Int, contentTitle: String?, contentText: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        setChannel(manager)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(smallIcon)
            .setProgress(0, 0, false)  // Remove indeterminate progress
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    @JvmStatic
    @Synchronized
    fun cancel(context: Context, id: Int = NOTIFICATION_ID) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(id)
    }

    @JvmStatic
    fun cancelAll(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancelAll()
    }

    @JvmStatic
    fun setChannel(notificationManager: NotificationManager?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                "OLE Sync",
                importance
            )
            notificationChannel.description = "Notifications for synchronization process"
            notificationChannel.setSound(null, null)  // Disable sound
            notificationChannel.enableVibration(false)  // Disable vibration
            notificationManager?.createNotificationChannel(notificationChannel)
        }
    }
}