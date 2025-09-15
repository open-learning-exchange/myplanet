package org.ole.planet.myplanet.repository

interface NotificationRepository {
    suspend fun getUnreadCount(userId: String?): Int
    suspend fun updateResourceNotification(userId: String?)
    suspend fun markNotificationsAsRead(notificationIds: List<String>)
}
