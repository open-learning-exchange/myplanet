package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmNotification

interface NotificationRepository {
    suspend fun getUnreadCount(userId: String?): Int
    suspend fun updateResourceNotification(userId: String?)
    suspend fun getNotifications(userId: String, filter: String): List<RealmNotification>
    suspend fun markAsRead(notificationId: String)
    suspend fun markAllAsRead(userId: String)
}
