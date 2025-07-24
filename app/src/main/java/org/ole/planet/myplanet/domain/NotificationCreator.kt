package org.ole.planet.myplanet.domain

import io.realm.Realm
import io.realm.Sort
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.ui.dashboard.DashboardViewModel
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.NotificationUtil
import org.ole.planet.myplanet.utilities.TimeUtils

class NotificationCreator(
    private val viewModel: DashboardViewModel,
    private val notificationManager: NotificationUtil.NotificationManager
) {
    fun createNotifications(realm: Realm, userId: String?): List<NotificationUtil.NotificationConfig> {
        val newNotifications = mutableListOf<NotificationUtil.NotificationConfig>()

        viewModel.updateResourceNotification(realm, userId)

        newNotifications += createSurveyNotifications(realm, userId)
        newNotifications += createTaskNotifications(realm, userId)
        newNotifications += createStorageNotification(realm, userId)
        newNotifications += createJoinRequestNotifications(realm, userId)

        return newNotifications
    }

    private fun createSurveyNotifications(realm: Realm, userId: String?): List<NotificationUtil.NotificationConfig> {
        val notifications = mutableListOf<NotificationUtil.NotificationConfig>()
        val pendingSurveys = viewModel.getPendingSurveys(realm, userId)
        val surveyTitles = viewModel.getSurveyTitlesFromSubmissions(realm, pendingSurveys)

        surveyTitles.forEach { title ->
            val notificationKey = "survey-$title"
            if (!notificationManager.hasNotificationBeenShown(notificationKey)) {
                viewModel.createNotificationIfNotExists(realm, "survey", title, title, userId)
                val config = notificationManager.createSurveyNotification(title, title)
                notifications.add(config)
            }
        }
        return notifications
    }

    private fun createTaskNotifications(realm: Realm, userId: String?): List<NotificationUtil.NotificationConfig> {
        val notifications = mutableListOf<NotificationUtil.NotificationConfig>()
        val tasks = realm.where(RealmTeamTask::class.java)
            .notEqualTo("status", "archived")
            .equalTo("completed", false)
            .equalTo("assignee", userId)
            .sort("deadline", Sort.ASCENDING)
            .findAll()

        tasks.forEach { task ->
            val notificationKey = "task-${task.id}"
            if (!notificationManager.hasNotificationBeenShown(notificationKey)) {
                viewModel.createNotificationIfNotExists(
                    realm,
                    "task",
                    "${task.title} ${TimeUtils.formatDate(task.deadline)}",
                    task.id,
                    userId
                )
                val config = notificationManager.createTaskNotification(
                    task.id ?: "task",
                    task.title ?: "New Task",
                    TimeUtils.formatDate(task.deadline)
                )
                notifications.add(config)
            }
        }
        return notifications
    }

    private fun createStorageNotification(realm: Realm, userId: String?): List<NotificationUtil.NotificationConfig> {
        val notifications = mutableListOf<NotificationUtil.NotificationConfig>()
        val storageRatio = FileUtils.totalAvailableMemoryRatio
        val notificationKey = "storage-critical"

        if (storageRatio > 85 && !notificationManager.hasNotificationBeenShown(notificationKey)) {
            viewModel.createNotificationIfNotExists(realm, "storage", "$storageRatio%", "storage", userId)
            val config = notificationManager.createStorageWarningNotification(storageRatio.toInt())
            notifications.add(config)
        }
        return notifications
    }

    private fun createJoinRequestNotifications(realm: Realm, userId: String?): List<NotificationUtil.NotificationConfig> {
        val notifications = mutableListOf<NotificationUtil.NotificationConfig>()
        val teamLeaderMemberships = realm.where(RealmMyTeam::class.java)
            .equalTo("userId", userId)
            .equalTo("docType", "membership")
            .equalTo("isLeader", true)
            .findAll()

        teamLeaderMemberships.forEach { leadership ->
            val pendingJoinRequests = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", leadership.teamId)
                .equalTo("docType", "request")
                .findAll()

            pendingJoinRequests.forEach { joinRequest ->
                val notificationKey = "join_request-${joinRequest._id}"
                if (!notificationManager.hasNotificationBeenShown(notificationKey)) {
                    val team = realm.where(RealmMyTeam::class.java)
                        .equalTo("_id", leadership.teamId)
                        .findFirst()
                    val requester = realm.where(RealmUserModel::class.java)
                        .equalTo("id", joinRequest.userId)
                        .findFirst()
                    val requesterName = requester?.name ?: "Unknown User"
                    val teamName = team?.name ?: "Unknown Team"
                    val message = "$requesterName has requested to join $teamName"

                    viewModel.createNotificationIfNotExists(
                        realm,
                        "join_request",
                        message,
                        joinRequest._id,
                        userId
                    )
                    val config = notificationManager.createJoinRequestNotification(
                        joinRequest._id!!,
                        requesterName,
                        teamName
                    )
                    notifications.add(config)
                }
            }
        }
        return notifications
    }
}

