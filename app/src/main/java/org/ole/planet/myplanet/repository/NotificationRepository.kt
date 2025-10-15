package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmNotification

enum class NotificationFilter {
    ALL,
    READ,
    UNREAD,
}

interface NotificationRepository {
    suspend fun getNotifications(userId: String?, filter: NotificationFilter): List<RealmNotification>
    suspend fun getUnreadNotifications(userId: String?): List<RealmNotification>
    suspend fun getUnreadCount(userId: String?): Int
    suspend fun updateResourceNotification(userId: String?)
    suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String>
    suspend fun markAllUnreadAsRead(userId: String?): Set<String>
    suspend fun createNotificationIfMissing(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    )
}
