package org.ole.planet.myplanet.repository

import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel

class NotificationRepositoryImpl @Inject constructor(
        databaseService: DatabaseService,
    ) : RealmRepository(databaseService), NotificationRepository {
    override suspend fun markNotificationAsRead(notificationId: String, userId: String?) {
        if (notificationId.startsWith("summary_")) {
            val type = notificationId.removePrefix("summary_")
            executeTransaction { realm ->
                realm.where(RealmNotification::class.java)
                    .equalTo("userId", userId)
                    .equalTo("type", type)
                    .equalTo("isRead", false)
                    .findAll()
                    .forEach { it.isRead = true }
            }
        } else {
            executeTransaction { realm ->
                val notification = realm.where(RealmNotification::class.java)
                    .equalTo("id", notificationId)
                    .findFirst()
                notification?.isRead = true
            }
        }
    }

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
        return queryList(RealmNotification::class.java) {
            equalTo("userId", userId)
            notEqualTo("message", "INVALID")
            isNotEmpty("message")
            when (filter) {
                "read" -> equalTo("isRead", true)
                "unread" -> equalTo("isRead", false)
            }
            sort("isRead", io.realm.Sort.ASCENDING, "createdAt", io.realm.Sort.DESCENDING)
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

    override fun getJoinRequestDetails(relatedId: String?): Pair<String, String> {
        return databaseService.withRealm { realm ->
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

    override fun getTaskTeamName(taskTitle: String): String? {
        return databaseService.withRealm { realm ->
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
