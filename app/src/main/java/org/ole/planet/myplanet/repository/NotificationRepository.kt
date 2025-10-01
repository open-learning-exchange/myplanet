package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmNotification

data class JoinRequestNotificationMetadata(
    val requesterName: String?,
    val teamName: String?,
)

data class TaskNotificationMetadata(
    val teamName: String?,
)

data class SurveyNotificationTarget(
    val stepId: String,
)

data class TaskNavigationTarget(
    val teamId: String,
    val teamName: String?,
    val teamType: String?,
)

data class JoinRequestNavigationTarget(
    val teamId: String,
)

interface NotificationRepository {
    suspend fun getUnreadCount(userId: String?): Int
    suspend fun updateResourceNotification(userId: String?)
    suspend fun getNotifications(userId: String, filter: String): List<RealmNotification>
    suspend fun markAsRead(notificationId: String)
    suspend fun markAllAsRead(userId: String)
    suspend fun getJoinRequestMetadata(joinRequestId: String?): JoinRequestNotificationMetadata?
    suspend fun getTaskNotificationMetadata(taskTitle: String): TaskNotificationMetadata?
    suspend fun ensureNotification(type: String, message: String, relatedId: String?, userId: String?)
    suspend fun resolveSurveyStepId(stepName: String?): SurveyNotificationTarget?
    suspend fun resolveTaskNavigation(taskId: String?): TaskNavigationTarget?
    suspend fun resolveJoinRequestTeam(joinRequestId: String?): JoinRequestNavigationTarget?
}
