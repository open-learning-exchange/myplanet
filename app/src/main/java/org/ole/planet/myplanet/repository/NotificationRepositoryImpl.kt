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

        val resourceCount = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }.count { resource ->
            resource.userId?.contains(userId) == true && resource.needToUpdate()
        }

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

        val notification = existingNotification?.apply {
            if (this.message != trimmedMessage) {
                this.message = trimmedMessage
                this.createdAt = now
            }
            this.type = type
            this.relatedId = relatedId
            this.userId = ownerId
        } ?: RealmNotification().apply {
            id = notificationId
            this.userId = ownerId
            this.type = type
            this.message = trimmedMessage
            this.relatedId = relatedId
            this.createdAt = now
        }

        save(notification)
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

