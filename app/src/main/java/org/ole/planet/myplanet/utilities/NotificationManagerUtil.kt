package org.ole.planet.myplanet.utilities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import java.text.SimpleDateFormat
import java.util.*

class NotificationManagerUtil(private val context: Context) {

    companion object {
        // Notification Channels
        const val CHANNEL_GENERAL = "general_notifications"
        const val CHANNEL_SURVEYS = "survey_notifications"
        const val CHANNEL_TASKS = "task_notifications"
        const val CHANNEL_SYSTEM = "system_notifications"
        const val CHANNEL_TEAM = "team_notifications"

        // Notification Types
        const val TYPE_SURVEY = "survey"
        const val TYPE_TASK = "task"
        const val TYPE_STORAGE = "storage"
        const val TYPE_JOIN_REQUEST = "join_request"
        const val TYPE_RESOURCE = "resource"
        const val TYPE_COURSE = "course"

        // Preferences
        private const val PREFS_NAME = "notification_preferences"
        private const val KEY_ENABLED = "notifications_enabled"
        private const val KEY_SURVEY_ENABLED = "survey_notifications_enabled"
        private const val KEY_TASK_ENABLED = "task_notifications_enabled"
        private const val KEY_SYSTEM_ENABLED = "system_notifications_enabled"
        private const val KEY_TEAM_ENABLED = "team_notifications_enabled"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_START = "quiet_hours_start"
        private const val KEY_QUIET_END = "quiet_hours_end"
        private const val KEY_ACTIVE_NOTIFICATIONS = "active_notifications"
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val activeNotifications = mutableSetOf<String>()

    init {
        loadActiveNotifications()
        createNotificationChannels()
    }

    data class NotificationConfig(
        val id: String,
        val type: String,
        val title: String,
        val message: String,
        val priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        val category: String = NotificationCompat.CATEGORY_MESSAGE,
        val actionable: Boolean = false,
        val bigTextStyle: Boolean = true,
        val autoCancel: Boolean = true,
        val silent: Boolean = false,
        val targetActivity: Class<*>? = null,
        val extras: Map<String, String> = emptyMap()
    )

    // Create notification channels for Android 8.0+
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_GENERAL,
                    "General Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "General app notifications"
                    enableLights(true)
                    enableVibration(true)
                },

                NotificationChannel(
                    CHANNEL_SURVEYS,
                    "Survey Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New surveys and survey reminders"
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                },

                NotificationChannel(
                    CHANNEL_TASKS,
                    "Task Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Task assignments and deadlines"
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                },

                NotificationChannel(
                    CHANNEL_SYSTEM,
                    "System Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Storage warnings and system updates"
                    enableLights(true)
                    enableVibration(false)
                },

                NotificationChannel(
                    CHANNEL_TEAM,
                    "Team Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Team join requests and team updates"
                    enableLights(true)
                    enableVibration(true)
                }
            )

            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            channels.forEach { channel ->
                systemNotificationManager.createNotificationChannel(channel)
            }
        }
    }

    // Show a notification with the given configuration
    fun showNotification(config: NotificationConfig): Boolean {
        if (!canShowNotification(config.type)) {
            return false
        }

        if (isQuietHours() && !isUrgentNotification(config)) {
            scheduleNotificationForLater(config)
            return false
        }

        try {
            val notification = buildNotification(config)
            val notificationId = config.id.hashCode()

            notificationManager.notify(notificationId, notification)
            markNotificationAsShown(config.id)

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun buildNotification(config: NotificationConfig): android.app.Notification {
        val channelId = getChannelForType(config.type)
        val intent = createNotificationIntent(config)
        val pendingIntent = PendingIntent.getActivity(
            context,
            config.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(getIconForType(config.type))
            .setContentTitle(config.title)
            .setContentText(config.message)
            .setPriority(config.priority)
            .setCategory(config.category)
            .setContentIntent(pendingIntent)
            .setAutoCancel(config.autoCancel)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setColor(ContextCompat.getColor(context, R.color.colorPrimary))

        if (!config.silent) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        if (config.bigTextStyle) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(config.message))
        }

        if (config.actionable) {
            addNotificationActions(builder, config)
        }

        return builder.build()
    }

    private fun addNotificationActions(builder: NotificationCompat.Builder, config: NotificationConfig) {
        when (config.type) {
            TYPE_SURVEY -> {
                val openIntent = createNotificationIntent(config)
                val openPendingIntent = PendingIntent.getActivity(
                    context, (config.id + "_open").hashCode(), openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.survey, "Take Survey", openPendingIntent)
            }

            TYPE_TASK -> {
                val openIntent = createNotificationIntent(config)
                val openPendingIntent = PendingIntent.getActivity(
                    context, (config.id + "_open").hashCode(), openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.team, "View Task", openPendingIntent)
            }

            TYPE_JOIN_REQUEST -> {
                val openIntent = createNotificationIntent(config)
                val openPendingIntent = PendingIntent.getActivity(
                    context, (config.id + "_open").hashCode(), openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.business, "Review", openPendingIntent)
            }
        }
    }

    private fun createNotificationIntent(config: NotificationConfig): Intent {
        val targetClass = config.targetActivity ?: DashboardActivity::class.java
        return Intent(context, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", config.type)
            putExtra("notification_id", config.id)
            putExtra("from_notification", true)
            config.extras.forEach { (key, value) ->
                putExtra(key, value)
            }
        }
    }

    // Check if notifications can be shown for a specific type
    private fun canShowNotification(type: String): Boolean {
        if (!notificationManager.areNotificationsEnabled()) {
            return false
        }

        if (!preferences.getBoolean(KEY_ENABLED, true)) {
            return false
        }

        return when (type) {
            TYPE_SURVEY -> preferences.getBoolean(KEY_SURVEY_ENABLED, true)
            TYPE_TASK -> preferences.getBoolean(KEY_TASK_ENABLED, true)
            TYPE_STORAGE, TYPE_RESOURCE, TYPE_COURSE -> preferences.getBoolean(KEY_SYSTEM_ENABLED, true)
            TYPE_JOIN_REQUEST -> preferences.getBoolean(KEY_TEAM_ENABLED, true)
            else -> true
        }
    }

    // Check if it's currently quiet hours
    private fun isQuietHours(): Boolean {
        if (!preferences.getBoolean(KEY_QUIET_HOURS_ENABLED, false)) {
            return false
        }

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTime = currentHour * 60 + currentMinute

        val quietStart = preferences.getInt(KEY_QUIET_START, 22 * 60) // 10 PM default
        val quietEnd = preferences.getInt(KEY_QUIET_END, 8 * 60) // 8 AM default

        return if (quietStart < quietEnd) {
            currentTime in quietStart..quietEnd
        } else {
            currentTime >= quietStart || currentTime <= quietEnd
        }
    }

    private fun isUrgentNotification(config: NotificationConfig): Boolean {
        return config.priority == NotificationCompat.PRIORITY_HIGH ||
                config.type == TYPE_TASK && config.message.contains("urgent", ignoreCase = true)
    }

    private fun scheduleNotificationForLater(config: NotificationConfig) {
        // Store notification to show after quiet hours
        // Implementation depends on your scheduling mechanism
    }

    private fun getChannelForType(type: String): String {
        return when (type) {
            TYPE_SURVEY -> CHANNEL_SURVEYS
            TYPE_TASK -> CHANNEL_TASKS
            TYPE_STORAGE, TYPE_RESOURCE, TYPE_COURSE -> CHANNEL_SYSTEM
            TYPE_JOIN_REQUEST -> CHANNEL_TEAM
            else -> CHANNEL_GENERAL
        }
    }

    private fun getIconForType(type: String): Int {
        return when (type) {
            TYPE_SURVEY -> R.drawable.survey
            TYPE_TASK -> R.drawable.team
            TYPE_STORAGE -> android.R.drawable.stat_sys_warning
            TYPE_JOIN_REQUEST -> R.drawable.business
            TYPE_RESOURCE -> R.drawable.ourlibrary
            TYPE_COURSE -> R.drawable.ourcourses
            else -> R.drawable.ic_home
        }
    }

    // Preference management methods
    fun setNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun areNotificationsEnabled(): Boolean {
        return preferences.getBoolean(KEY_ENABLED, true) && notificationManager.areNotificationsEnabled()
    }

    fun setSurveyNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SURVEY_ENABLED, enabled).apply()
    }

    fun setTaskNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_TASK_ENABLED, enabled).apply()
    }

    fun setSystemNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SYSTEM_ENABLED, enabled).apply()
    }

    fun setTeamNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_TEAM_ENABLED, enabled).apply()
    }

    fun setQuietHours(enabled: Boolean, startHour: Int = 22, startMinute: Int = 0,
                      endHour: Int = 8, endMinute: Int = 0) {
        preferences.edit()
            .putBoolean(KEY_QUIET_HOURS_ENABLED, enabled)
            .putInt(KEY_QUIET_START, startHour * 60 + startMinute)
            .putInt(KEY_QUIET_END, endHour * 60 + endMinute)
            .apply()
    }

    // Notification tracking methods
    private fun markNotificationAsShown(notificationId: String) {
        activeNotifications.add(notificationId)
        saveActiveNotifications()
    }

    fun hasNotificationBeenShown(notificationId: String): Boolean {
        return activeNotifications.contains(notificationId)
    }

    fun clearNotification(notificationId: String) {
        notificationManager.cancel(notificationId.hashCode())
        activeNotifications.remove(notificationId)
        saveActiveNotifications()
    }

    fun clearAllNotifications() {
        notificationManager.cancelAll()
        activeNotifications.clear()
        saveActiveNotifications()
    }

    private fun loadActiveNotifications() {
        val saved = preferences.getStringSet(KEY_ACTIVE_NOTIFICATIONS, emptySet()) ?: emptySet()
        activeNotifications.addAll(saved)
    }

    private fun saveActiveNotifications() {
        preferences.edit().putStringSet(KEY_ACTIVE_NOTIFICATIONS, activeNotifications).apply()
    }

    // Clean up old notifications (call periodically)
    fun cleanupOldNotifications(maxAgeHours: Int = 168) { // 7 days default
        val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000)

        // Remove old notifications from tracking
        // This would require storing timestamps with notification IDs
        // Implementation depends on your specific needs
    }

    // Utility methods for creating common notification types
    fun createSurveyNotification(surveyId: String, surveyTitle: String): NotificationConfig {
        return NotificationConfig(
            id = "survey_$surveyId",
            type = TYPE_SURVEY,
            title = "📋 New Survey Available",
            message = surveyTitle,
            priority = NotificationCompat.PRIORITY_HIGH,
            category = NotificationCompat.CATEGORY_REMINDER,
            actionable = true,
            extras = mapOf("surveyId" to surveyId)
        )
    }

    fun createTaskNotification(taskId: String, taskTitle: String, deadline: String): NotificationConfig {
        val priority = if (isTaskUrgent(deadline)) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        return NotificationConfig(
            id = "task_$taskId",
            type = TYPE_TASK,
            title = "✅ New Task Assigned",
            message = "$taskTitle\nDue: $deadline",
            priority = priority,
            category = NotificationCompat.CATEGORY_REMINDER,
            actionable = true,
            extras = mapOf("taskId" to taskId)
        )
    }

    fun createJoinRequestNotification(requestId: String, requesterName: String, teamName: String): NotificationConfig {
        return NotificationConfig(
            id = "join_request_$requestId",
            type = TYPE_JOIN_REQUEST,
            title = "👥 Team Join Request",
            message = "$requesterName wants to join $teamName",
            priority = NotificationCompat.PRIORITY_DEFAULT,
            category = NotificationCompat.CATEGORY_SOCIAL,
            actionable = true,
            extras = mapOf("requestId" to requestId, "teamName" to teamName)
        )
    }

    fun createStorageWarningNotification(storagePercentage: Int): NotificationConfig {
        val priority = if (storagePercentage > 95) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        return NotificationConfig(
            id = "storage_warning",
            type = TYPE_STORAGE,
            title = "⚠️ Storage Warning",
            message = "Device storage is at $storagePercentage%. Consider freeing up space.",
            priority = priority,
            category = NotificationCompat.CATEGORY_STATUS,
            actionable = true
        )
    }

    private fun isTaskUrgent(deadline: String): Boolean {
        return try {
            val deadlineTime = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(deadline)?.time ?: return false
            val currentTime = System.currentTimeMillis()
            val timeDiff = deadlineTime - currentTime
            val daysUntilDeadline = timeDiff / (1000 * 60 * 60 * 24)
            daysUntilDeadline <= 2
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}