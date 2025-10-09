package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmNotification

interface NotificationRepository {
    suspend fun getNotificationsForUser(userId: String, filter: NotificationFilter): List<RealmNotification>
    suspend fun getUnreadCount(userId: String?): Int
    suspend fun updateResourceNotification(userId: String?)
    suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String>
    suspend fun markAllUnreadAsRead(userId: String?): Set<String>
}
