package org.ole.planet.myplanet.repository

interface NotificationRepository {
    suspend fun getNotifications(userId: String, filter: String): List<org.ole.planet.myplanet.model.Notification>
    suspend fun getUnreadCount(userId: String?): Int
    suspend fun updateResourceNotification(userId: String?, resourceCount: Int)
    suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String>
    suspend fun markAllUnreadAsRead(userId: String?): Set<String>
    suspend fun createNotificationIfMissing(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    )
}
