package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel

class NotificationRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
    @ApplicationContext private val context: Context
) : RealmRepository(databaseService), NotificationRepository {
    override suspend fun createNotificationIfMissing(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    ) {
        val actualUserId = userId ?: ""
        executeTransaction { realm ->
            val query = realm.where(RealmNotification::class.java)
                .equalTo("userId", actualUserId)
                .equalTo("type", type)

            val existingNotification =
                if (relatedId != null) {
                    query.equalTo("relatedId", relatedId).findFirst()
                } else {
                    query.isNull("relatedId").findFirst()
                }

            if (existingNotification == null) {
                realm.createObject(RealmNotification::class.java, UUID.randomUUID().toString()).apply {
                    this.userId = actualUserId
                    this.type = type
                    this.message = message
                    this.relatedId = relatedId
                    this.createdAt = Date()
                }
            }
        }
    }

    override suspend fun getUnreadCount(userId: String?): Int {
        if (userId == null) return 0

        return count(RealmNotification::class.java) {
            equalTo("userId", userId)
            equalTo("isRead", false)
        }.toInt()
    }

    override suspend fun updateResourceNotification(userId: String?, resourceCount: Int) {
        userId ?: return

        val notificationId = "$userId:resource:count"
        val existingNotification = findByField(RealmNotification::class.java, "id", notificationId)

        if (resourceCount > 0) {
            val previousCount = existingNotification?.message?.toIntOrNull() ?: 0
            val countChanged = previousCount != resourceCount

            val notification = existingNotification?.apply {
                message = "$resourceCount"
                relatedId = "$resourceCount"
                if (countChanged) {
                    this.isRead = false
                    this.createdAt = Date()
                }
            } ?: RealmNotification().apply {
                this.id = notificationId
                this.userId = userId
                this.type = "resource"
                this.message = "$resourceCount"
                this.relatedId = "$resourceCount"
                this.createdAt = Date()
            }
            save(notification)
        } else {
            existingNotification?.let { delete(RealmNotification::class.java, "id", it.id) }
        }
    }

    override suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String> {
        if (notificationIds.isEmpty()) return emptySet()

        val updatedIds = mutableSetOf<String>()
        executeTransaction { realm ->
            realm.where(RealmNotification::class.java)
                .`in`("id", notificationIds.toTypedArray())
                .findAll()
                ?.forEach { notification ->
                    notification.isRead = true
                    notification.createdAt = Date()
                    updatedIds.add(notification.id)
                }
        }
        return updatedIds
    }

    override suspend fun markAllUnreadAsRead(userId: String?): Set<String> {
        val actualUserId = userId ?: return emptySet()
        val updatedIds = mutableSetOf<String>()
        val now = Date()
        executeTransaction { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", actualUserId)
                .equalTo("isRead", false)
                .findAll()
                ?.forEach { notification ->
                    notification.isRead = true
                    notification.createdAt = now
                    updatedIds.add(notification.id)
                }
        }
        return updatedIds
    }

    override suspend fun getNotifications(userId: String, filter: String): List<RealmNotification> {
        return databaseService.withRealm { realm ->
            val query = realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
            when (filter) {
                "read" -> query.equalTo("isRead", true)
                "unread" -> query.equalTo("isRead", false)
            }
            val notifications = query.sort("isRead", io.realm.Sort.ASCENDING, "createdAt", io.realm.Sort.DESCENDING).findAll()
                .filter { it.message.isNotEmpty() && it.message != "INVALID" }

            val unmanagedNotifications = realm.copyFromRealm(notifications)

            unmanagedNotifications.forEach { notification ->
                notification.message = formatNotificationMessage(notification)
            }
            unmanagedNotifications
        }
    }

    private fun formatNotificationMessage(notification: RealmNotification): String {
        return when (notification.type.lowercase()) {
            "survey" -> context.getString(R.string.pending_survey_notification) + " ${notification.message}"
            "task" -> {
                val datePattern = Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")
                val matcher = datePattern.matcher(notification.message)
                if (matcher.find()) {
                    val taskTitle = notification.message.substring(0, matcher.start()).trim()
                    val dateValue = notification.message.substring(matcher.start()).trim()
                    formatTaskNotification(context, taskTitle, dateValue)
                } else {
                    notification.message
                }
            }
            "resource" -> {
                notification.message.toIntOrNull()?.let { count ->
                    context.getString(R.string.resource_notification, count)
                } ?: notification.message
            }
            "storage" -> {
                val storageValue = notification.message.replace("%", "").toIntOrNull()
                storageValue?.let {
                    when {
                        it <= 10 -> context.getString(R.string.storage_running_low) + " ${it}%"
                        it <= 40 -> context.getString(R.string.storage_running_low) + " ${it}%"
                        else -> context.getString(R.string.storage_available) + " ${it}%"
                    }
                } ?: notification.message
            }
            "join_request" -> {
                databaseService.withRealm { realm ->
                    val joinRequest = notification.relatedId?.let {
                        realm.where(RealmMyTeam::class.java)
                            .equalTo("_id", it)
                            .equalTo("docType", "request")
                            .findFirst()
                    }
                    val team = joinRequest?.teamId?.let { tid ->
                        realm.where(RealmMyTeam::class.java)
                            .equalTo("_id", tid)
                            .findFirst()
                    }
                    val requester = joinRequest?.userId?.let { uid ->
                        realm.where(RealmUserModel::class.java)
                            .equalTo("id", uid)
                            .findFirst()
                    }
                    val requesterName = requester?.name ?: "Unknown User"
                    val teamName = team?.name ?: "Unknown Team"
                    "<b>${context.getString(R.string.join_request_prefix)}</b> " +
                            context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
                }
            }
            else -> notification.message
        }
    }

    private fun formatTaskNotification(context: Context, taskTitle: String, dateValue: String): String {
        return databaseService.withRealm { realm ->
            val taskObj = realm.where(RealmTeamTask::class.java)
                .equalTo("title", taskTitle)
                .findFirst()
            val team = realm.where(RealmMyTeam::class.java)
                .equalTo("_id", taskObj?.teamId)
                .findFirst()
            if (team?.name != null) {
                "<b>${team.name}</b>: ${context.getString(R.string.task_notification, taskTitle, dateValue)}"
            } else {
                context.getString(R.string.task_notification, taskTitle, dateValue)
            }
        }
    }
}

