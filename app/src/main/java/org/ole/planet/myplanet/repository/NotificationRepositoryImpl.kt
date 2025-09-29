package org.ole.planet.myplanet.repository

import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel

class NotificationRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), NotificationRepository {

    override suspend fun getUnreadCount(userId: String?): Int {
        if (userId == null) return 0

        return count(RealmNotification::class.java) {
            equalTo("userId", userId)
            equalTo("isRead", false)
        }.toInt()
    }

    override suspend fun updateResourceNotification(userId: String?) {
        userId ?: return

        val resourceCount = withRealmAsync { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("isPrivate", false)
                .contains("userId", userId)
                .beginGroup()
                    .equalTo("resourceOffline", false)
                    .or()
                    .isNotNull("resourceLocalAddress")
                    .and()
                    .notEqualTo("_rev", "downloadedRev")
                .endGroup()
                .count().toInt()
        }

        val existingNotification = queryList(RealmNotification::class.java) {
            equalTo("userId", userId)
            equalTo("type", "resource")
        }.firstOrNull()

        if (resourceCount > 0) {
            val notification = existingNotification?.apply {
                message = "$resourceCount"
                relatedId = "$resourceCount"
            } ?: RealmNotification().apply {
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

    override suspend fun ensureNotification(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    ) {
        val ownerId = userId ?: ""
        val trimmedMessage = message.trim()
        val notificationId = buildNotificationId(type, relatedId, ownerId, trimmedMessage)

        if (trimmedMessage.isEmpty()) {
            delete(RealmNotification::class.java, "id", notificationId)
            return
        }

        val existingNotification = findByField(RealmNotification::class.java, "id", notificationId)
        val now = Date()

        val (teamName, requesterName) = preComputeMetadata(type, relatedId, trimmedMessage)

        val notification = existingNotification?.apply {
            if (this.message != trimmedMessage) {
                this.message = trimmedMessage
                this.createdAt = now
            }
            this.type = type
            this.relatedId = relatedId
            this.userId = ownerId
            this.teamName = teamName
            this.requesterName = requesterName
        } ?: RealmNotification().apply {
            id = notificationId
            this.userId = ownerId
            this.type = type
            this.message = trimmedMessage
            this.relatedId = relatedId
            this.createdAt = now
            this.teamName = teamName
            this.requesterName = requesterName
        }

        save(notification)
    }

    private suspend fun preComputeMetadata(type: String, relatedId: String?, message: String): Pair<String?, String?> {
        return when (type.lowercase()) {
            "task" -> {
                val taskTitle = extractTaskTitle(message)
                val task = findByField(RealmTeamTask::class.java, "title", taskTitle)
                val teamName = task?.teamId?.let { teamId ->
                    findByField(RealmMyTeam::class.java, "_id", teamId)?.name
                }
                teamName to null
            }
            "join_request" -> {
                val sanitizedId = relatedId?.removePrefix("join_request_") ?: return null to null
                val joinRequest = queryList(RealmMyTeam::class.java) {
                    equalTo("_id", sanitizedId)
                    equalTo("docType", "request")
                }.firstOrNull()

                val teamName = joinRequest?.teamId?.let { teamId ->
                    findByField(RealmMyTeam::class.java, "_id", teamId)?.name
                }
                val requesterName = joinRequest?.userId?.let { userId ->
                    findByField(RealmUserModel::class.java, "id", userId)?.name
                }
                teamName to requesterName
            }
            else -> null to null
        }
    }

    private fun extractTaskTitle(message: String): String {
        val datePattern = java.util.regex.Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")
        val matcher = datePattern.matcher(message)
        return if (matcher.find()) {
            message.substring(0, matcher.start()).trim()
        } else {
            message
        }
    }

    override suspend fun getNotifications(userId: String, filter: String): List<RealmNotification> {
        return queryList(RealmNotification::class.java) {
            equalTo("userId", userId)
            when (filter) {
                "read" -> equalTo("isRead", true)
                "unread" -> equalTo("isRead", false)
            }
            sort("createdAt", Sort.DESCENDING)
        }.filter { it.message.isNotEmpty() && it.message != "INVALID" }
    }

    override suspend fun markAsRead(notificationId: String) {
        update(RealmNotification::class.java, "id", notificationId) { it.isRead = true }
    }

    override suspend fun markAllAsRead(userId: String) {
        executeTransaction { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .findAll()
                .forEach { it.isRead = true }
        }
    }

    override suspend fun getJoinRequestMetadata(joinRequestId: String?): JoinRequestNotificationMetadata? {
        val rawId = joinRequestId?.takeUnless { it.isBlank() } ?: return null
        val sanitizedId = rawId.removePrefix("join_request_")

        val joinRequest = queryList(RealmMyTeam::class.java) {
            equalTo("_id", sanitizedId)
            equalTo("docType", "request")
        }.firstOrNull() ?: return null

        val teamName = joinRequest.teamId?.let { teamId ->
            findByField(RealmMyTeam::class.java, "_id", teamId)?.name
        }

        val requesterName = joinRequest.userId?.let { userId ->
            findByField(RealmUserModel::class.java, "id", userId)?.name
        }

        return JoinRequestNotificationMetadata(requesterName, teamName)
    }

    override suspend fun getTaskNotificationMetadata(taskTitle: String): TaskNotificationMetadata? {
        if (taskTitle.isBlank()) return null

        val task = findByField(RealmTeamTask::class.java, "title", taskTitle) ?: return null

        val teamName = task.teamId?.let { teamId ->
            findByField(RealmMyTeam::class.java, "_id", teamId)?.name
        }

        return TaskNotificationMetadata(teamName)
    }

    override suspend fun getNotificationsWithUnreadCount(userId: String, filter: String): Pair<List<RealmNotification>, Int> {
        return withRealmAsync { realm ->
            val notificationsQuery = realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)

            when (filter) {
                "read" -> notificationsQuery.equalTo("isRead", true)
                "unread" -> notificationsQuery.equalTo("isRead", false)
            }

            val notifications = notificationsQuery
                .sort("createdAt", Sort.DESCENDING)
                .findAll()
                .filter { it.message.isNotEmpty() && it.message != "INVALID" }
                .map { realm.copyFromRealm(it) }

            val unreadCount = realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .count().toInt()

            notifications to unreadCount
        }
    }

    override suspend fun batchEnsureNotifications(notifications: List<NotificationData>, userId: String?) {
        val ownerId = userId ?: ""
        if (notifications.isEmpty()) return

        executeTransaction { realm ->
            notifications.forEach { notificationData ->
                val trimmedMessage = notificationData.message.trim()
                if (trimmedMessage.isEmpty()) return@forEach

                val notificationId = buildNotificationId(notificationData.type, notificationData.relatedId, ownerId, trimmedMessage)

                val existingNotification = realm.where(RealmNotification::class.java)
                    .equalTo("id", notificationId)
                    .findFirst()

                val now = Date()

                if (existingNotification != null) {
                    if (existingNotification.message != trimmedMessage) {
                        existingNotification.message = trimmedMessage
                        existingNotification.createdAt = now
                    }
                    existingNotification.type = notificationData.type
                    existingNotification.relatedId = notificationData.relatedId
                    existingNotification.userId = ownerId
                } else {
                    val newNotification = realm.createObject(RealmNotification::class.java, notificationId)
                    newNotification.userId = ownerId
                    newNotification.type = notificationData.type
                    newNotification.message = trimmedMessage
                    newNotification.relatedId = notificationData.relatedId
                    newNotification.createdAt = now
                }
            }
        }
    }
}

private fun buildNotificationId(
    type: String,
    relatedId: String?,
    userId: String,
    message: String,
): String {
    val relatedKey = relatedId?.takeUnless { it.isBlank() } ?: message
    return listOf(userId, type, relatedKey).joinToString(":")
}

