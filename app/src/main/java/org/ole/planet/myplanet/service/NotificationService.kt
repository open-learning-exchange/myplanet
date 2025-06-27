package org.ole.planet.myplanet.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import java.util.concurrent.atomic.AtomicInteger

object NotificationService {
    private const val CHANNEL_ID = "planet_notifications"
    private const val CHANNEL_NAME = "Planet Notifications"
    private const val CHANNEL_DESCRIPTION = "Notifications from Planet Learning"
    private const val GROUP_KEY = "org.ole.planet.myplanet.NOTIFICATIONS"
    private const val MAX_NOTIFICATIONS = 10
    private const val PREFS_NAME = "NotificationPrefs"
    private const val SHOWN_NOTIFICATIONS_KEY = "shown_notifications"
    private const val NOTIFICATION_DELAY_MS = 100L // Delay between notifications

    private val notificationIdCounter = AtomicInteger(2000)
    private val shownNotificationIds = mutableSetOf<String>()
    private val notificationIdMap = mutableMapOf<String, Int>()

    fun createNotificationChannel(context: Context): Boolean {
        Log.d("NotificationService", "createNotificationChannel() called")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel != null) {
                Log.d("NotificationService", "Channel already exists with importance: ${existingChannel.importance}")
                return true
            }

            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                // Prevent notifications from being grouped automatically by system
                setGroup(null)
            }

            try {
                notificationManager.createNotificationChannel(channel)
                Log.d("NotificationService", "Notification channel created successfully: $CHANNEL_ID")
                val verifyChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (verifyChannel != null) {
                    Log.d("NotificationService", "Channel verification successful. Importance: ${verifyChannel.importance}")
                    return true
                } else {
                    Log.e("NotificationService", "Channel creation failed - verification returned null")
                    return false
                }
            } catch (e: Exception) {
                Log.e("NotificationService", "Error creating notification channel", e)
                return false
            }
        } else {
            Log.d("NotificationService", "Android version < O, no channel needed")
            return true
        }
    }

    suspend fun showPendingNotifications(context: Context, forceShow: Boolean = false) {
        Log.d("NotificationService", "showPendingNotifications() called, forceShow: $forceShow")

        try {
            // Check notification permission first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    Log.w("NotificationService", "Notifications are disabled for this app")
                    return
                }
            }

            // Initialize Realm safely
            initializeRealm(context)

            withContext(Dispatchers.IO) {
                var realm: Realm? = null
                try {
                    realm = Realm.getDefaultInstance()

                    if (!forceShow) {
                        loadShownNotifications(context)
                    }

                    val notifications = realm.where(RealmNotification::class.java)
                        .equalTo("isRead", false)
                        .findAll()
                        .toList()
                        .sortedByDescending { it.createdAt }
                        .take(MAX_NOTIFICATIONS)

                    Log.d("NotificationService", "Found ${notifications.size} unread notifications")

                    val newNotifications = if (forceShow) {
                        notifications
                    } else {
                        notifications.filterNot { shownNotificationIds.contains(it.id) }
                    }

                    Log.d("NotificationService", "New notifications to show: ${newNotifications.size}")

                    if (newNotifications.isNotEmpty()) {
                        if (!createNotificationChannel(context)) {
                            Log.e("NotificationService", "Failed to create notification channel")
                            return@withContext
                        }

                        showNotificationsSequentially(context, newNotifications)
                    } else {
                        Log.d("NotificationService", "No new notifications to show")
                    }
                } catch (e: Exception) {
                    Log.e("NotificationService", "Error in showPendingNotifications", e)
                } finally {
                    realm?.close()
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationService", "Critical error in showPendingNotifications", e)
        }
    }

    private suspend fun showNotificationsSequentially(context: Context, notifications: List<RealmNotification>) {
        var successCount = 0

        for ((index, notification) in notifications.withIndex()) {
            try {
                val systemNotificationId = showNotification(context, notification, index)
                if (systemNotificationId != -1) {
                    shownNotificationIds.add(notification.id)
                    notificationIdMap[notification.id] = systemNotificationId
                    successCount++
                    Log.d("NotificationService", "Successfully showed notification: ${notification.title ?: "Untitled"}")
                }

                // Add delay between notifications to prevent system overwhelm
                if (index < notifications.size - 1) {
                    delay(NOTIFICATION_DELAY_MS)
                }
            } catch (e: Exception) {
                Log.e("NotificationService", "Error showing notification: ${notification.title}", e)
            }
        }

        // Save after all notifications are processed
        if (successCount > 0) {
            saveShownNotifications(context)

            // Only show summary if there are many notifications (5 or more)
            if (successCount >= 5) {
                try {
                    delay(500) // Ensure individual notifications are shown first
                    showSummaryNotification(context, successCount)
                } catch (e: Exception) {
                    Log.e("NotificationService", "Error showing summary notification", e)
                }
            }
        }

        Log.d("NotificationService", "Successfully showed $successCount out of ${notifications.size} notifications")
    }

    private fun showNotification(context: Context, notification: RealmNotification, index: Int = 0): Int {
        val systemNotificationId = notificationIdCounter.getAndIncrement()

        try {
            val intent = Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("notificationId", notification.id)
                putExtra("notificationType", notification.type)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                systemNotificationId,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            // Create notification with individual styling (no grouping for now)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ole_logo)
                .setContentTitle(notification.title ?: "Planet Notification")
                .setContentText(notification.message ?: "New notification")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setWhen(notification.createdAt.time)
                .setShowWhen(true)
                .setOngoing(false)
                .setTimeoutAfter(0) // Don't auto-dismiss
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)
                .setVibrate(longArrayOf(0, 250, 250, 250)) // Custom vibration pattern

            // Add expanded text if message is long
            if (!notification.message.isNullOrEmpty() && notification.message!!.length > 50) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(notification.message)
                        .setBigContentTitle(notification.title ?: "Planet Notification")
                )
            }

            // Add notification type specific styling
            when (notification.type) {
                "sync" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        builder.setColor(context.getColor(android.R.color.holo_blue_dark))
                    }
                }
                "update" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        builder.setColor(context.getColor(android.R.color.holo_orange_dark))
                    }
                }
                "message" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        builder.setColor(context.getColor(android.R.color.holo_green_dark))
                    }
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        builder.setColor(context.getColor(android.R.color.holo_blue_light))
                    }
                }
            }

            val notificationManager = NotificationManagerCompat.from(context)

            // Double-check we can post notifications
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w("NotificationService", "Notifications disabled, cannot show notification")
                return -1
            }

            // Check for permission on newer Android versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w("NotificationService", "Missing POST_NOTIFICATIONS permission")
                    return -1
                }
            }

            notificationManager.notify(systemNotificationId, builder.build())
            Log.d("NotificationService", "Notification posted with ID: $systemNotificationId")

            return systemNotificationId

        } catch (e: SecurityException) {
            Log.e("NotificationService", "SecurityException: Missing notification permission", e)
            return -1
        } catch (e: Exception) {
            Log.e("NotificationService", "Error showing notification", e)
            return -1
        }
    }

    private fun showSummaryNotification(context: Context, count: Int) {
        val summaryId = 1999

        try {
            val intent = Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("showNotifications", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                summaryId,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("$count new Planet notifications")
                .setContentText("You have $count new notifications from Planet Learning")
                .setSmallIcon(R.drawable.ole_logo)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(false)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)

            // Check for permission before showing notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w("NotificationService", "Missing POST_NOTIFICATIONS permission")
                    return
                }
            }

            notificationManager.notify(summaryId, summaryNotification)
            Log.d("NotificationService", "Summary notification posted for $count notifications")

        } catch (e: Exception) {
            Log.e("NotificationService", "Error showing summary notification", e)
        }
    }

    private fun initializeRealm(context: Context) {
        try {
            if (Realm.getGlobalInstanceCount(Realm.getDefaultConfiguration()).toLong() == 0L) {
                Realm.init(context)
            }
        } catch (e: Exception) {
            try {
                Realm.init(context)
            } catch (initException: Exception) {
                Log.e("NotificationService", "Failed to initialize Realm", initException)
                throw initException
            }
        }
    }

    suspend fun onUserSync(context: Context) {
        Log.d("NotificationService", "onUserSync() called")
        try {
            showPendingNotifications(context, forceShow = false)
        } catch (e: Exception) {
            Log.e("NotificationService", "Error in onUserSync", e)
        }
    }

    suspend fun onUserLogin(context: Context) {
        Log.d("NotificationService", "onUserLogin() called")
        try {
            // Clear previous tracking and show all notifications
            clearShownNotifications(context)
            // Add a small delay to ensure UI is ready
            delay(1000)
            showPendingNotifications(context, forceShow = true)
        } catch (e: Exception) {
            Log.e("NotificationService", "Error in onUserLogin", e)
        }
    }

    fun markNotificationAsRead(context: Context, notificationDbId: String) {
        // Cancel the system notification
        cancelNotification(context, notificationDbId)

        // Mark as read in database
        var realm: Realm? = null
        try {
            initializeRealm(context)
            realm = Realm.getDefaultInstance()
            realm.executeTransaction {
                val notification = realm.where(RealmNotification::class.java)
                    .equalTo("id", notificationDbId)
                    .findFirst()
                notification?.isRead = true
            }
        } catch (e: Exception) {
            Log.e("NotificationService", "Error marking notification as read", e)
        } finally {
            realm?.close()
        }
    }

    private fun saveShownNotifications(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putStringSet(SHOWN_NOTIFICATIONS_KEY, shownNotificationIds.toSet())
                .apply()
            Log.d("NotificationService", "Saved ${shownNotificationIds.size} shown notification IDs")
        } catch (e: Exception) {
            Log.e("NotificationService", "Error saving shown notifications", e)
        }
    }

    private fun loadShownNotifications(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedIds = prefs.getStringSet(SHOWN_NOTIFICATIONS_KEY, emptySet()) ?: emptySet()
            shownNotificationIds.clear()
            shownNotificationIds.addAll(savedIds)
            Log.d("NotificationService", "Loaded ${shownNotificationIds.size} shown notification IDs")
        } catch (e: Exception) {
            Log.e("NotificationService", "Error loading shown notifications", e)
        }
    }

    private fun clearShownNotifications(context: Context) {
        shownNotificationIds.clear()
        notificationIdMap.clear()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(SHOWN_NOTIFICATIONS_KEY).apply()
            Log.d("NotificationService", "Cleared shown notifications tracking")
        } catch (e: Exception) {
            Log.e("NotificationService", "Error clearing shown notifications", e)
        }
    }

    fun resetNotificationTracking(context: Context) {
        clearShownNotifications(context)
        notificationIdCounter.set(2000)
        Log.d("NotificationService", "Notification tracking reset")
    }

    fun clearAllNotifications(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancelAll()
            resetNotificationTracking(context)
            Log.d("NotificationService", "All notifications cleared")
        } catch (e: Exception) {
            Log.e("NotificationService", "Error clearing notifications", e)
        }
    }

    fun cancelNotification(context: Context, notificationDbId: String) {
        notificationIdMap[notificationDbId]?.let { systemId ->
            try {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(systemId)
                notificationIdMap.remove(notificationDbId)
                shownNotificationIds.remove(notificationDbId)
                saveShownNotifications(context)
                Log.d("NotificationService", "Cancelled notification: $systemId")
            } catch (e: Exception) {
                Log.e("NotificationService", "Error cancelling notification", e)
            }
        }
    }

    fun checkNotificationSetup(context: Context): String {
        val notificationManager = NotificationManagerCompat.from(context)
        val areEnabled = notificationManager.areNotificationsEnabled()

        val channelStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = systemManager.getNotificationChannel(CHANNEL_ID)
            if (channel != null) {
                "Channel exists with importance: ${channel.importance}"
            } else {
                "Channel does not exist"
            }
        } else {
            "Pre-O device, no channel needed"
        }

        return "Notifications enabled: $areEnabled\n$channelStatus\nShown notifications: ${shownNotificationIds.size}"
    }
}