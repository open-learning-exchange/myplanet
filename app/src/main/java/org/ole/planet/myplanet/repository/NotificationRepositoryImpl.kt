package org.ole.planet.myplanet.repository

import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.json.JSONObject
import org.ole.planet.myplanet.model.RealmStepExam
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
        }.count { it.needToUpdate() && it.userId?.contains(userId) == true }

        val existingNotification = findByField(RealmNotification::class.java, "userId", userId)
            ?.takeIf { it.type == "resource" }

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

        return withRealm { realm ->
            val joinRequest = realm.where(RealmMyTeam::class.java)
                .equalTo("_id", sanitizedId)
                .equalTo("docType", "request")
                .findFirst()

            joinRequest?.let {
                val teamName = it.teamId?.let { teamId ->
                    realm.where(RealmMyTeam::class.java)
                        .equalTo("_id", teamId)
                        .findFirst()
                        ?.name
                }

                val requesterName = it.userId?.let { userId ->
                    realm.where(RealmUserModel::class.java)
                        .equalTo("id", userId)
                        .findFirst()
                        ?.name
                }

                JoinRequestNotificationMetadata(requesterName, teamName)
            }
        }
    }

    override suspend fun getTaskNotificationMetadata(taskTitle: String): TaskNotificationMetadata? {
        if (taskTitle.isBlank()) return null

        return withRealm { realm ->
            val task = realm.where(RealmTeamTask::class.java)
                .equalTo("title", taskTitle)
                .findFirst()

            task?.let {
                val teamName = it.teamId?.let { teamId ->
                    realm.where(RealmMyTeam::class.java)
                        .equalTo("_id", teamId)
                        .findFirst()
                        ?.name
                }

                TaskNotificationMetadata(teamName)
            }
        }
    }

    override suspend fun getSurveyNotificationDestination(examTitle: String?): SurveyNotificationDestination? {
        val normalizedTitle = examTitle?.takeUnless { it.isBlank() } ?: return null
        val exam = findByField(RealmStepExam::class.java, "name", normalizedTitle)
        val examId = exam?.id?.takeUnless { it.isNullOrBlank() }
        return examId?.let { SurveyNotificationDestination(it) }
    }

    override suspend fun getTaskNotificationDestination(taskId: String?): TaskNotificationDestination? {
        val normalizedTaskId = taskId?.takeUnless { it.isBlank() } ?: return null
        val task =
            findByField(RealmTeamTask::class.java, "id", normalizedTaskId)
                ?: findByField(RealmTeamTask::class.java, "_id", normalizedTaskId)
                ?: return null

        val teamId = task.teamId?.takeUnless { it.isNullOrBlank() } ?: extractTeamId(task.link) ?: return null
        val team = findByField(RealmMyTeam::class.java, "_id", teamId)

        return TaskNotificationDestination(
            teamId = teamId,
            teamName = team?.name,
            teamType = team?.type,
        )
    }

    override suspend fun getJoinRequestDestination(joinRequestId: String?): JoinRequestNotificationDestination? {
        val rawId = joinRequestId?.takeUnless { it.isBlank() } ?: return null
        val sanitizedId = rawId.removePrefix("join_request_")

        val joinRequest = queryList(RealmMyTeam::class.java) {
            equalTo("_id", sanitizedId)
            equalTo("docType", "request")
        }.firstOrNull() ?: return null

        val teamId = joinRequest.teamId?.takeUnless { it.isNullOrBlank() } ?: return null
        val team = findByField(RealmMyTeam::class.java, "_id", teamId)

        return JoinRequestNotificationDestination(
            teamId = teamId,
            teamName = team?.name,
        )
    }

    private fun extractTeamId(link: String?): String? {
        if (link.isNullOrBlank()) return null
        return runCatching { JSONObject(link).optString("teams") }
            .getOrNull()
            ?.takeUnless { it.isNullOrBlank() }
    }
}

