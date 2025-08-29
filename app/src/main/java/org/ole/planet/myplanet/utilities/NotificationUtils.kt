package org.ole.planet.myplanet.utilities

import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity

object NotificationUtils {
    const val CHANNEL_GENERAL = "general_notifications"
    const val CHANNEL_SURVEYS = "survey_notifications"
    const val CHANNEL_TASKS = "task_notifications"
    const val CHANNEL_SYSTEM = "system_notifications"
    const val CHANNEL_TEAM = "team_notifications"
    const val TYPE_SURVEY = "survey"
    const val TYPE_TASK = "task"
    const val TYPE_STORAGE = "storage"
    const val TYPE_JOIN_REQUEST = "join_request"
    const val TYPE_RESOURCE = "resource"
    const val TYPE_COURSE = "course"
    private const val PREFS_NAME = "notification_preferences"
    private const val KEY_ENABLED = "notifications_enabled"
    private const val KEY_SURVEY_ENABLED = "survey_notifications_enabled"
    private const val KEY_TASK_ENABLED = "task_notifications_enabled"
    private const val KEY_SYSTEM_ENABLED = "system_notifications_enabled"
    private const val KEY_TEAM_ENABLED = "team_notifications_enabled"
    private const val KEY_ACTIVE_NOTIFICATIONS = "active_notifications"
    const val ACTION_MARK_AS_READ = "mark_as_read"
    const val ACTION_OPEN_NOTIFICATION = "open_notification"
    const val ACTION_STORAGE_SETTINGS = "storage_settings"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_NOTIFICATION_TYPE = "notification_type"
    const val EXTRA_RELATED_ID = "related_id"

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
        val extras: Map<String, String> = emptyMap(),
        val relatedId: String? = null
    )

    @JvmStatic
    fun create(context: Context, smallIcon: Int, contentTitle: String?, contentText: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
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
    fun setChannel(notificationManager: android.app.NotificationManager?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = android.app.NotificationManager.IMPORTANCE_LOW
            val notificationChannel = NotificationChannel("11", "ole", importance)
            notificationManager?.createNotificationChannel(notificationChannel)
        }
    }

    @JvmStatic
    fun getInstance(context: Context): NotificationManager {
        return NotificationManager(context)
    }

    class NotificationManager(private val context: Context) {
        private val notificationManager = NotificationManagerCompat.from(context)
        private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        private val activeNotifications = mutableSetOf<String>()
        private val sessionShownNotifications = mutableSetOf<String>()

        init {
            loadActiveNotifications()
            createNotificationChannels()
        }

        private fun createNotificationChannels() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channels = listOf(
                    NotificationChannel(CHANNEL_GENERAL, "General Notifications", IMPORTANCE_DEFAULT).apply {
                        description = "General app notifications"
                        enableLights(true)
                        enableVibration(true)
                    },

                    NotificationChannel(CHANNEL_SURVEYS, "Survey Notifications", IMPORTANCE_HIGH).apply {
                        description = "New surveys and survey reminders"
                        enableLights(true)
                        enableVibration(true)
                        setShowBadge(true)
                    },

                    NotificationChannel(CHANNEL_TASKS, "Task Notifications", IMPORTANCE_HIGH).apply {
                        description = "Task assignments and deadlines"
                        enableLights(true)
                        enableVibration(true)
                        setShowBadge(true)
                    },

                    NotificationChannel(CHANNEL_SYSTEM, "System Notifications", IMPORTANCE_DEFAULT).apply {
                        description = "Storage warnings and system updates"
                        enableLights(true)
                        enableVibration(false)
                    },

                    NotificationChannel(CHANNEL_TEAM, "Team Notifications", IMPORTANCE_DEFAULT).apply {
                        description = "Team join requests and team updates"
                        enableLights(true)
                        enableVibration(true)
                    }
                )

                val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                channels.forEach { channel ->
                    systemNotificationManager.createNotificationChannel(channel)
                }
            }
        }

        fun showNotification(config: NotificationConfig): Boolean {
            if (!canShowNotification(config.type)) {
                return false
            }

            if (sessionShownNotifications.contains(config.id)) {
                return false
            }

            val notificationId = config.id.hashCode()
            val activeNotifications = notificationManager.activeNotifications
            val isAlreadyShowing = activeNotifications.any { it.id == notificationId }
            
            if (isAlreadyShowing) {
                return false
            }

            try {
                val notification = buildNotification(config)
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
            val markAsReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_MARK_AS_READ
                putExtra(EXTRA_NOTIFICATION_ID, config.id)
            }
            val markAsReadPendingIntent = PendingIntent.getBroadcast(
                context, (config.id + "_mark_read").hashCode(), markAsReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ole_logo, "Mark as Read", markAsReadPendingIntent)

            when (config.type) {
                TYPE_SURVEY -> {
                    val openIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = ACTION_OPEN_NOTIFICATION
                        putExtra(EXTRA_NOTIFICATION_TYPE, config.type)
                        putExtra(EXTRA_NOTIFICATION_ID, config.id)
                        putExtra(EXTRA_RELATED_ID, config.relatedId)
                    }
                    val openPendingIntent = PendingIntent.getBroadcast(
                        context, (config.id + "_open").hashCode(), openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(R.drawable.survey, "Take Survey", openPendingIntent)
                }

                TYPE_TASK -> {
                    val openIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = ACTION_OPEN_NOTIFICATION
                        putExtra(EXTRA_NOTIFICATION_TYPE, config.type)
                        putExtra(EXTRA_NOTIFICATION_ID, config.id)
                        putExtra(EXTRA_RELATED_ID, config.relatedId)
                    }
                    val openPendingIntent = PendingIntent.getBroadcast(
                        context, (config.id + "_open").hashCode(), openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(R.drawable.team, "View Task", openPendingIntent)
                }

                TYPE_STORAGE -> {
                    val storageIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = ACTION_STORAGE_SETTINGS
                        putExtra(EXTRA_NOTIFICATION_ID, config.id)
                    }
                    val storagePendingIntent = PendingIntent.getBroadcast(
                        context, (config.id + "_storage").hashCode(), storageIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(R.drawable.ole_logo, "Storage Settings", storagePendingIntent)
                }

                TYPE_JOIN_REQUEST -> {
                    val openIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = ACTION_OPEN_NOTIFICATION
                        putExtra(EXTRA_NOTIFICATION_TYPE, config.type)
                        putExtra(EXTRA_NOTIFICATION_ID, config.id)
                        putExtra(EXTRA_RELATED_ID, config.relatedId)
                    }
                    val openPendingIntent = PendingIntent.getBroadcast(
                        context, (config.id + "_open").hashCode(), openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(R.drawable.business, "Review", openPendingIntent)
                }

                TYPE_RESOURCE -> {
                    val openIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = ACTION_OPEN_NOTIFICATION
                        putExtra(EXTRA_NOTIFICATION_TYPE, config.type)
                        putExtra(EXTRA_NOTIFICATION_ID, config.id)
                        putExtra(EXTRA_RELATED_ID, config.relatedId)
                    }
                    val openPendingIntent = PendingIntent.getBroadcast(
                        context, (config.id + "_open").hashCode(), openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(R.drawable.ourlibrary, "View Resource", openPendingIntent)
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

        private fun canShowNotification(type: String): Boolean {
            val notificationsEnabled = notificationManager.areNotificationsEnabled()
            
            if (!notificationsEnabled) {
                return false
            }

            val globalEnabled = preferences.getBoolean(KEY_ENABLED, true)
            
            if (!globalEnabled) {
                return false
            }

            val typeEnabled = when (type) {
                TYPE_SURVEY -> preferences.getBoolean(KEY_SURVEY_ENABLED, true)
                TYPE_TASK -> preferences.getBoolean(KEY_TASK_ENABLED, true)
                TYPE_STORAGE, TYPE_RESOURCE, TYPE_COURSE -> preferences.getBoolean(KEY_SYSTEM_ENABLED, true)
                TYPE_JOIN_REQUEST -> preferences.getBoolean(KEY_TEAM_ENABLED, true)
                else -> true
            }
            
            return typeEnabled
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

        private fun markNotificationAsShown(notificationId: String) {
            activeNotifications.add(notificationId)
            sessionShownNotifications.add(notificationId)
            saveActiveNotifications()
        }

        fun clearNotification(notificationId: String) {
            notificationManager.cancel(notificationId.hashCode())
            activeNotifications.remove(notificationId)
            saveActiveNotifications()
        }

        fun clearSessionTracking() {
            sessionShownNotifications.clear()
        }

        private fun loadActiveNotifications() {
            val saved = preferences.getStringSet(KEY_ACTIVE_NOTIFICATIONS, emptySet()) ?: emptySet()
            activeNotifications.addAll(saved)
        }

        private fun saveActiveNotifications() {
            preferences.edit { putStringSet(KEY_ACTIVE_NOTIFICATIONS, activeNotifications) }
        }

        fun createSurveyNotification(surveyId: String, surveyTitle: String): NotificationConfig {
            return NotificationConfig(
                id = surveyId,
                type = TYPE_SURVEY,
                title = "📋 New Survey Available",
                message = surveyTitle,
                priority = NotificationCompat.PRIORITY_HIGH,
                category = NotificationCompat.CATEGORY_REMINDER,
                actionable = true,
                extras = mapOf("surveyId" to surveyId),
                relatedId = surveyId
            )
        }

        fun createTaskNotification(taskId: String, taskTitle: String, deadline: String): NotificationConfig {
            val priority = if (isTaskUrgent(deadline)) {
                NotificationCompat.PRIORITY_HIGH
            } else {
                NotificationCompat.PRIORITY_DEFAULT
            }

            return NotificationConfig(
                id = taskId,
                type = TYPE_TASK,
                title = "✅ New Task Assigned",
                message = "$taskTitle\nDue: $deadline",
                priority = priority,
                category = NotificationCompat.CATEGORY_REMINDER,
                actionable = true,
                extras = mapOf("taskId" to taskId),
                relatedId = taskId
            )
        }

        fun createJoinRequestNotification(requestId: String, requesterName: String, teamName: String): NotificationConfig {
            return NotificationConfig(
                id = requestId,
                type = TYPE_JOIN_REQUEST,
                title = "👥 Team Join Request",
                message = "$requesterName wants to join $teamName",
                priority = NotificationCompat.PRIORITY_DEFAULT,
                category = NotificationCompat.CATEGORY_SOCIAL,
                actionable = true,
                extras = mapOf("requestId" to requestId, "teamName" to teamName),
                relatedId = requestId
            )
        }

        fun createStorageWarningNotification(storagePercentage: Int, customId: String): NotificationConfig {
            val priority = if (storagePercentage > 95) {
                NotificationCompat.PRIORITY_HIGH
            } else {
                NotificationCompat.PRIORITY_DEFAULT
            }

            return NotificationConfig(
                id = customId,
                type = TYPE_STORAGE,
                title = "⚠️ Storage Warning",
                message = "Device storage is at $storagePercentage%. Consider freeing up space.",
                priority = priority,
                category = NotificationCompat.CATEGORY_STATUS,
                actionable = true,
                relatedId = "storage"
            )
        }

        fun createResourceNotification(notificationId: String, resourceCount: Int): NotificationConfig {
            return NotificationConfig(
                id = notificationId,
                type = TYPE_RESOURCE,
                title = "📚 New Resources Available",
                message = "$resourceCount new resources have been added",
                priority = NotificationCompat.PRIORITY_DEFAULT,
                category = NotificationCompat.CATEGORY_RECOMMENDATION,
                actionable = true,
                extras = mapOf("resourceCount" to resourceCount.toString()),
                relatedId = notificationId
            )
        }

        private fun isTaskUrgent(deadline: String): Boolean {
            return try {
                val deadlineTime = TimeUtils.parseDate(deadline) ?: return false
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
}

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var databaseService: DatabaseService
    override fun onReceive(context: Context, intent: Intent) {
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
    }
    
    private fun markNotificationAsRead(context: Context, notificationId: String?) {
        if (notificationId == null) {
            return
        }


        try {
            databaseService.withRealm { realm ->
                realm.executeTransaction { r ->
                    val notification = r.where(RealmNotification::class.java)
                        .equalTo("id", notificationId)
                        .findFirst()

                    if (notification != null) {
                        notification.isRead = true
                    }
                }
            }

            MainApplication.applicationScope.launch(Dispatchers.Main) {
                delay(200)
                val broadcastIntent = Intent("org.ole.planet.myplanet.NOTIFICATION_READ_FROM_SYSTEM")
                broadcastIntent.setPackage(context.packageName)
                broadcastIntent.putExtra("notification_id", notificationId)
                context.sendBroadcast(broadcastIntent)

                try {
                    val localBroadcastIntent = Intent("org.ole.planet.myplanet.NOTIFICATION_READ_FROM_SYSTEM_LOCAL")
                    localBroadcastIntent.putExtra("notification_id", notificationId)
                    androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(localBroadcastIntent)
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
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
