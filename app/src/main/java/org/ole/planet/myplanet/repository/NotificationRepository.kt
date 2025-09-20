package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmNotification

data class JoinRequestNotificationMetadata(
    val requesterName: String?,
    val teamName: String?,
)

data class TaskNotificationMetadata(
    val teamName: String?,
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
}
