package org.ole.planet.myplanet.ui.dashboard

import android.app.Application
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.TeamNotificationInfo
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.NotificationConfig
import org.ole.planet.myplanet.utils.NotificationUtils

class NotificationsLoader @Inject constructor(
    private val application: Application,
    private val notificationsRepository: NotificationsRepository,
    private val resourcesRepository: ResourcesRepository,
    private val teamsRepository: TeamsRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun updateResourceNotification(userId: String?) {
        val resourceCount = resourcesRepository.countLibrariesNeedingUpdate(userId)
        notificationsRepository.updateResourceNotification(userId, resourceCount)
    }

    suspend fun createNotificationIfMissing(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    ) {
        notificationsRepository.createNotificationIfMissing(type, message, relatedId, userId)
    }

    suspend fun getUnreadNotificationsSize(userId: String?): Int {
        return notificationsRepository.getUnreadCount(userId)
    }

    suspend fun getTeamNotificationInfo(teamId: String, userId: String): TeamNotificationInfo {
        return notificationsRepository.getTeamNotificationInfo(teamId, userId)
    }

    suspend fun getTeamNotifications(teamIds: List<String>, userId: String): Map<String, TeamNotificationInfo> {
        return notificationsRepository.getTeamNotifications(teamIds, userId)
    }

    suspend fun checkAndCreateNewNotifications(userId: String?): Pair<Int, List<NotificationConfig>> = withContext(dispatcherProvider.io) {
        var unreadCount = 0
        val newNotifications = mutableListOf<NotificationConfig>()

        try {
            updateResourceNotification(userId)

            val taskData = teamsRepository.getTaskNotifications(userId)
            val joinRequestData = teamsRepository.getJoinRequestNotifications(userId)

            val pendingSurveys = submissionsRepository.getPendingSurveys(userId)
            val surveyTitles = submissionsRepository.getSurveyTitlesFromSubmissions(pendingSurveys)
            val storageRatio = FileUtils.totalAvailableMemoryRatio(application).toInt()
            val joinRequestTemplate = application.getString(R.string.user_requested_to_join_team)

            val realmNotifications = notificationsRepository.checkAndCreateNotifications(
                userId,
                taskData,
                joinRequestData,
                joinRequestTemplate,
                storageRatio,
                surveyTitles
            )

            val createdNotifications = realmNotifications.mapNotNull {
                createNotificationConfigFromDatabase(it)
            }
            newNotifications.addAll(createdNotifications)

            unreadCount = getUnreadNotificationsSize(userId)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val groupedNotifications = newNotifications.groupBy { it.type }
        val finalNotifications = mutableListOf<NotificationConfig>()

        groupedNotifications.forEach { (type, notifications) ->
            when {
                notifications.size == 1 -> {
                    finalNotifications.add(notifications.first())
                }
                notifications.size > 1 -> {
                    val summaryConfig = NotificationUtils.createSummaryNotification(type, notifications.size)
                    finalNotifications.add(summaryConfig)
                }
            }
        }

        return@withContext Pair(unreadCount, finalNotifications)
    }

    private fun createNotificationConfigFromDatabase(dbNotification: RealmNotification): NotificationConfig? {
        return when (dbNotification.type.lowercase()) {
            "survey" -> NotificationUtils.createSurveyNotification(
                dbNotification.id,
                dbNotification.message
            ).copy(
                extras = mapOf("surveyId" to (dbNotification.relatedId ?: dbNotification.id))
            )
            "task" -> {
                val parts = dbNotification.message.split(" ")
                val taskTitle = parts.dropLast(3).joinToString(" ")
                val deadline = parts.takeLast(3).joinToString(" ")
                NotificationUtils.createTaskNotification(dbNotification.id, taskTitle, deadline).copy(
                    extras = mapOf("taskId" to (dbNotification.relatedId ?: dbNotification.id))
                )
            }
            "resource" -> NotificationUtils.createResourceNotification(
                dbNotification.id,
                dbNotification.message.toIntOrNull() ?: 0
            )
            "storage" -> {
                val storageValue = dbNotification.message.replace("%", "").toIntOrNull() ?: 0
                NotificationUtils.createStorageWarningNotification(storageValue, dbNotification.id)
            }
            "join_request" -> NotificationUtils.createJoinRequestNotification(
                dbNotification.id,
                "New Request",
                dbNotification.message
            ).copy(
                extras = mapOf("requestId" to (dbNotification.relatedId ?: dbNotification.id), "teamName" to dbNotification.message)
            )
            else -> null
        }
    }
}
