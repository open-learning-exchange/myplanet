package org.ole.planet.myplanet.repository

import java.util.Date
import java.util.UUID
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NotificationItem

class NotificationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    databaseService: DatabaseService,
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

    override suspend fun getUnreadCount(userId: String?): Int = withContext(Dispatchers.IO) {
        if (userId == null) return@withContext 0

        count(RealmNotification::class.java) {
            equalTo("userId", userId)
            equalTo("isRead", false)
        }.toInt()
    }

    override suspend fun updateResourceNotification(userId: String?, resourceCount: Int) = withContext(Dispatchers.IO) {
        userId ?: return@withContext

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

    override suspend fun markAllUnreadAsRead(userId: String?): Set<String> = withContext(Dispatchers.IO) {
        val actualUserId = userId ?: return@withContext emptySet()
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
        updatedIds
    }

    override suspend fun getNotifications(userId: String, filter: String): List<NotificationItem> {
        val notifications = queryList(RealmNotification::class.java) {
            equalTo("userId", userId)
            notEqualTo("message", "INVALID")
            isNotEmpty("message")
            when (filter) {
                "read" -> equalTo("isRead", true)
                "unread" -> equalTo("isRead", false)
            }
            sort("isRead", io.realm.Sort.ASCENDING, "createdAt", io.realm.Sort.DESCENDING)
        }
        return notifications.map { notification ->
            formatNotificationMessage(notification)
        }
    }

    private suspend fun formatNotificationMessage(notification: RealmNotification): NotificationItem {
        var teamId: String? = null
        var teamName: String? = null
        var teamType: String? = null
        val message = when (notification.type.lowercase()) {
            "survey" -> context.getString(R.string.pending_survey_notification) + " ${notification.message}"
            "task" -> {
                val datePattern = Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")
                val matcher = datePattern.matcher(notification.message)
                if (matcher.find()) {
                    val taskTitle = notification.message.substring(0, matcher.start()).trim()
                    val dateValue = notification.message.substring(matcher.start()).trim()
                    val (tId, tName, tType) = getTaskDetails(notification.relatedId) ?: Triple(null, null, null)
                    teamId = tId
                    teamName = tName
                    teamType = tType
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
                teamId = getJoinRequestTeamId(notification.relatedId)
                val (requesterName, tName) = getJoinRequestDetails(notification.relatedId)
                teamName = tName
                "<b>${context.getString(R.string.join_request_prefix)}</b> " +
                        context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
            }
            else -> notification.message
        }
        return NotificationItem(
            id = notification.id,
            message = message,
            isRead = notification.isRead,
            createdAt = notification.createdAt,
            type = notification.type,
            relatedId = notification.relatedId,
            teamId = teamId,
            teamName = teamName,
            teamType = teamType
        )
    }

    private suspend fun formatTaskNotification(context: Context, taskTitle: String, dateValue: String): String {
        val teamName = getTaskTeamName(taskTitle)
        return if (teamName != null) {
            "<b>$teamName</b>: ${context.getString(R.string.task_notification, taskTitle, dateValue)}"
        } else {
            context.getString(R.string.task_notification, taskTitle, dateValue)
        }
    }

    override suspend fun getSurveyId(relatedId: String?): String? {
        return relatedId?.let {
            withRealm { realm ->
                realm.where(org.ole.planet.myplanet.model.RealmStepExam::class.java)
                    .equalTo("name", it)
                    .findFirst()?.id
            }
        }
    }

    override suspend fun getTaskDetails(relatedId: String?): Triple<String, String?, String?>? {
        return relatedId?.let {
            withRealm { realm ->
                val task = realm.where(org.ole.planet.myplanet.model.RealmTeamTask::class.java)
                    .equalTo("id", it)
                    .findFirst()
                val linkJson = org.json.JSONObject(task?.link ?: "{}")
                val teamId = linkJson.optString("teams")
                if (teamId.isNotEmpty()) {
                    val teamObject = realm.where(org.ole.planet.myplanet.model.RealmMyTeam::class.java)
                        .equalTo("_id", teamId)
                        .findFirst()
                    Triple(teamId, teamObject?.name, teamObject?.type)
                } else {
                    null
                }
            }
        }
    }

    override suspend fun getJoinRequestTeamId(relatedId: String?): String? {
        return relatedId?.let {
            val actualJoinRequestId = if (it.startsWith("join_request_")) {
                it.removePrefix("join_request_")
            } else {
                it
            }
            withRealm { realm ->
                realm.where(org.ole.planet.myplanet.model.RealmMyTeam::class.java)
                    .equalTo("_id", actualJoinRequestId)
                    .equalTo("docType", "request")
                    .findFirst()?.teamId
            }
        }
    }

    override suspend fun getJoinRequestDetails(relatedId: String?): Pair<String, String> {
        return withRealm { realm ->
            val joinRequest = realm.where(RealmMyTeam::class.java)
                .equalTo("_id", relatedId)
                .equalTo("docType", "request")
                .findFirst()
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
            Pair(requester?.name ?: "Unknown User", team?.name ?: "Unknown Team")
        }
    }

    override suspend fun getTaskTeamName(taskTitle: String): String? {
        return withRealm { realm ->
            val taskObj = realm.where(RealmTeamTask::class.java)
                .equalTo("title", taskTitle)
                .findFirst()
            val team = realm.where(RealmMyTeam::class.java)
                .equalTo("_id", taskObj?.teamId)
                .findFirst()
            team?.name
        }
    }
}
