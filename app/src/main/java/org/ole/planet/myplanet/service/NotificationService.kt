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
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import java.util.concurrent.atomic.AtomicInteger

object NotificationService {
    private const val CHANNEL_ID = "planet_notifications"
    private const val CHANNEL_NAME = "Planet Notifications"
    private const val CHANNEL_DESCRIPTION = "Notifications from Planet Learning"
    private const val GROUP_KEY = "org.ole.planet.myplanet.NOTIFICATIONS"
    private const val MAX_NOTIFICATIONS = 5

    // Use atomic integer for unique notification IDs to avoid conflicts
    private val notificationIdCounter = AtomicInteger(1000)

    // Map to track already shown notifications with their system notification IDs
    private val shownNotificationIds = mutableSetOf<String>()
    private val notificationIdMap = mutableMapOf<String, Int>()

    /**
     * Creates notification channel for Android O and above
     */
    fun createNotificationChannel(context: Context): Boolean {
        Log.d("NotificationService", "createNotificationChannel() called")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Check if channel already exists
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel != null) {
                Log.d("NotificationService", "Channel already exists with importance: ${existingChannel.importance}")
                return true
            }

            val importance = NotificationManager.IMPORTANCE_HIGH // Changed to HIGH for better visibility
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            try {
                notificationManager.createNotificationChannel(channel)
                Log.d("NotificationService", "Notification channel created successfully: $CHANNEL_ID")

                // Verify channel was created
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

    /**
     * Shows app notifications as system notifications
     */

    suspend fun showPendingNotifications(context: Context) = withContext(Dispatchers.IO) @androidx.annotation.RequiresPermission(
        android.Manifest.permission.POST_NOTIFICATIONS
    ) {
        Log.d("NotificationService", "showPendingNotifications() called")

        // Check notification permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                Log.w("NotificationService", "Notifications are disabled for this app")
                return@withContext
            }
        }

        val realm = Realm.getDefaultInstance()
        try {
            // Get unread notifications and limit to avoid notification flood
            val notifications = realm.where(RealmNotification::class.java)
                .equalTo("isRead", false)
                .findAll()
                .toList()
                .takeLast(MAX_NOTIFICATIONS)

            Log.d("NotificationService", "Found ${notifications.size} unread notifications")

            // Check if we have any new notifications
            val newNotifications = notifications.filterNot { shownNotificationIds.contains(it.id) }
            Log.d("NotificationService", "New notifications to show: ${newNotifications.size}")

            if (newNotifications.isNotEmpty()) {
                if (!createNotificationChannel(context)) {
                    Log.e("NotificationService", "Failed to create notification channel")
                    return@withContext
                }

                // Show individual notifications with proper error handling
                newNotifications.forEach { notification ->
                    try {
                        val systemNotificationId = showNotification(context, notification)
                        if (systemNotificationId != -1) {
                            shownNotificationIds.add(notification.id)
                            notificationIdMap[notification.id] = systemNotificationId
                            Log.d("NotificationService", "Successfully showed notification: ${notification.title}")
                        }
                    } catch (e: Exception) {
                        Log.e("NotificationService", "Error showing notification: ${notification.title}", e)
                    }
                }

                // If we have multiple notifications, show a summary
                if (newNotifications.size > 1) {
                    try {
                        showSummaryNotification(context, newNotifications.size)
                    } catch (e: Exception) {
                        Log.e("NotificationService", "Error showing summary notification", e)
                    }
                }
            } else {
                Log.d("NotificationService", "No new notifications to show")
            }
        } catch (e: Exception) {
            Log.e("NotificationService", "Error in showPendingNotifications", e)
        } finally {
            realm.close()
        }
    }

    /**
     * Shows a single notification with proper ID management
     */
    private fun showNotification(context: Context, notification: RealmNotification): Int {
        val systemNotificationId = notificationIdCounter.getAndIncrement()

        try {
            val intent = Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("notificationId", notification.id)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                systemNotificationId, // Use unique ID for PendingIntent too
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ole_logo)
                .setContentTitle(notification.title ?: "Planet Notification")
                .setContentText(notification.message ?: "New notification")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            val notificationManager = NotificationManagerCompat.from(context)

            // Double-check we can post notifications
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w("NotificationService", "Notifications disabled, cannot show notification")
                return -1
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

    /**
     * Shows a summary notification for multiple notifications
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showSummaryNotification(context: Context, count: Int) {
        val summaryId = 0 // Use 0 for summary notification

        try {
            val intent = Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
                .setContentTitle("$count new notifications")
                .setContentText("You have $count new notifications from Planet Learning")
                .setSmallIcon(R.drawable.ole_logo)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(summaryId, summaryNotification)
            Log.d("NotificationService", "Summary notification posted")

        } catch (e: Exception) {
            Log.e("NotificationService", "Error showing summary notification", e)
        }
    }

    /**
     * Reset notification tracking (e.g., when user logs out)
     */
    fun resetNotificationTracking() {
        shownNotificationIds.clear()
        notificationIdMap.clear()
        notificationIdCounter.set(1000) // Reset counter
        Log.d("NotificationService", "Notification tracking reset")
    }

    /**
     * Clear all notifications
     */
    fun clearAllNotifications(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancelAll()
            resetNotificationTracking()
            Log.d("NotificationService", "All notifications cleared")
        } catch (e: Exception) {
            Log.e("NotificationService", "Error clearing notifications", e)
        }
    }

    /**
     * Cancel a specific notification
     */
    fun cancelNotification(context: Context, notificationDbId: String) {
        notificationIdMap[notificationDbId]?.let { systemId ->
            try {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(systemId)
                notificationIdMap.remove(notificationDbId)
                Log.d("NotificationService", "Cancelled notification: $systemId")
            } catch (e: Exception) {
                Log.e("NotificationService", "Error cancelling notification", e)
            }
        }
    }

    /**
     * Test method to create and show a simple notification
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showTestNotification(context: Context): Boolean {
        Log.d("NotificationService", "showTestNotification() called")

        // Ensure channel is created first
        if (!createNotificationChannel(context)) {
            Log.e("NotificationService", "Failed to create notification channel for test")
            return false
        }

        val testId = notificationIdCounter.getAndIncrement()
        Log.d("NotificationService", "Using test notification ID: $testId")

        try {
            // Check if notifications are enabled
            val notificationManager = NotificationManagerCompat.from(context)
            val areEnabled = notificationManager.areNotificationsEnabled()
            Log.d("NotificationService", "Are notifications enabled: $areEnabled")

            if (!areEnabled) {
                Log.w("NotificationService", "Notifications are disabled for this app")
                return false
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ole_logo)
                .setContentTitle("PERSISTENT Test Notification")
                .setContentText("This notification should stay visible - ${System.currentTimeMillis()}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // .setContentIntent(pendingIntent) // REMOVED - might cause auto-dismiss
                .setAutoCancel(false) // EXPLICITLY SET TO FALSE
                .setOngoing(true) // MAKE IT PERSISTENT/ONGOING
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())

            // Show the notification
            Log.d("NotificationService", "About to post PERSISTENT test notification")
            notificationManager.notify(testId, builder.build())
            Log.d("NotificationService", "PERSISTENT test notification posted successfully with ID: $testId")

            // Add a delayed check to see if notification is still there
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val activeNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        systemManager.activeNotifications.size
                    } catch (e: Exception) {
                        -1
                    }
                } else {
                    -1
                }
                Log.d("NotificationService", "Active notifications after 2 seconds: $activeNotifications")
            }, 2000)

            return true

        } catch (e: SecurityException) {
            Log.e("NotificationService", "SecurityException: Missing notification permission", e)
            return false
        } catch (e: Exception) {
            Log.e("NotificationService", "Error showing test notification", e)
            e.printStackTrace()
            return false
        }
    }

    /**
     * Check if notifications are properly set up
     */
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

        return "Notifications enabled: $areEnabled\n$channelStatus"
    }

    /**
     * Clear the test notification
     */
    fun clearTestNotification(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(9999) // Clear the simple test
            Log.d("NotificationService", "Test notification cleared")
        } catch (e: Exception) {
            Log.e("NotificationService", "Error clearing test notification", e)
        }
    }
}