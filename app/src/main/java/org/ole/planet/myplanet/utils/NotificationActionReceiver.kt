package org.ole.planet.myplanet.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.service.getBroadcastService
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var notificationsRepository: NotificationsRepository
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        MainApplication.applicationScope.launch {
            try {
                val action = intent.action
                val notificationId = intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_ID)

                when (action) {
                    NotificationUtils.ACTION_MARK_AS_READ -> {
                        markNotificationAsRead(context, notificationId)
                        notificationId?.let {
                            NotificationUtils.getInstance(context).clearNotification(it)
                        }
                    }

                    NotificationUtils.ACTION_STORAGE_SETTINGS -> {
                        markNotificationAsRead(context, notificationId)
                        val storageIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(storageIntent)
                        notificationId?.let {
                            NotificationUtils.getInstance(context).clearNotification(it)
                        }
                    }

                    NotificationUtils.ACTION_OPEN_NOTIFICATION -> {
                        markNotificationAsRead(context, notificationId)
                        val notificationType = intent.getStringExtra(NotificationUtils.EXTRA_NOTIFICATION_TYPE)
                        val relatedId = intent.getStringExtra(NotificationUtils.EXTRA_RELATED_ID)

                        val dashboardIntent = Intent(context, DashboardActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("notification_type", notificationType)
                            putExtra("notification_id", notificationId)
                            putExtra("related_id", relatedId)
                            putExtra("auto_navigate", true)
                        }
                        context.startActivity(dashboardIntent)
                        notificationId?.let {
                            NotificationUtils.getInstance(context).clearNotification(it)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun markNotificationAsRead(context: Context, notificationId: String?) {
        if (notificationId == null) {
            return
        }

        try {
            withContext(Dispatchers.IO) {
                notificationsRepository.markNotificationsAsRead(setOf(notificationId))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        withContext(Dispatchers.Main) {
            delay(200)
            val broadcastIntent = Intent("org.ole.planet.myplanet.NOTIFICATION_READ_FROM_SYSTEM")
            broadcastIntent.setPackage(context.packageName)
            broadcastIntent.putExtra("notification_id", notificationId)
            context.sendBroadcast(broadcastIntent)

            try {
                val localBroadcastIntent = Intent("org.ole.planet.myplanet.NOTIFICATION_READ_FROM_SYSTEM_LOCAL")
                localBroadcastIntent.putExtra("notification_id", notificationId)
                val broadcastService = getBroadcastService(context)
                broadcastService.sendBroadcast(localBroadcastIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val dashboardIntent = Intent(context, DashboardActivity::class.java)
                dashboardIntent.action = "REFRESH_NOTIFICATION_BADGE"
                dashboardIntent.putExtra("notification_id", notificationId)
                dashboardIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(dashboardIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
