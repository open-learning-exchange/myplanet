package org.ole.planet.myplanet.repository

interface NotificationRepository {
    suspend fun getUnreadCount(userId: String?): Int
    suspend fun updateResourceNotification(userId: String?, resourceCount: Int)
    suspend fun createNotificationIfNotExists(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?
    )
    suspend fun markAsRead(notificationId: String)
    suspend fun markAllAsRead(type: String, userId: String?)
    suspend fun getUnreadNotifications(userId: String?): List<org.ole.planet.myplanet.model.RealmNotification>
}
