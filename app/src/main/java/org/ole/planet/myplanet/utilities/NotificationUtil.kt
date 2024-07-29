package org.ole.planet.myplanet.utilities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationUtil {
    @JvmStatic
    fun create(context: Context, smallIcon: Int, contentTitle: String?, contentText: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val a = NotificationCompat.Builder(context, "11")
        setChannel(manager)
        val notification = a.setContentTitle(contentTitle).setContentText(contentText).setSmallIcon(smallIcon)
            .setProgress(0, 0, true).setAutoCancel(true).build()
        manager.notify(111, notification)
    }

    @JvmStatic
    fun cancel(context: Context, id: Int) {
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
            val notificationChannel = NotificationChannel("11", "ole", importance)
            notificationManager?.createNotificationChannel(notificationChannel)
        }
    }
}