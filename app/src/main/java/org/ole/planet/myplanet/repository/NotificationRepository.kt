package org.ole.planet.myplanet.repository

data class NotificationData(
    val type: String,
    val message: String,
    val relatedId: String?
)

interface NotificationRepository {
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
    suspend fun createNotificationsBatch(
        notifications: List<NotificationData>,
        userId: String?
    )
    suspend fun cleanupDuplicateNotifications(userId: String?)
}
