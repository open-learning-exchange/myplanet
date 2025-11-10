package org.ole.planet.myplanet.repository

interface NotificationRepository {
    suspend fun getNotifications(userId: String, filter: String): List<org.ole.planet.myplanet.model.RealmNotification>
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
    suspend fun createNotificationsIfMissing(
        notifications: List<NotificationData>,
        userId: String?,
    )

    data class NotificationData(
        val type: String,
        val message: String,
        val relatedId: String?,
    )
}
