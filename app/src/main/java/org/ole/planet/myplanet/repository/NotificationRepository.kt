package org.ole.planet.myplanet.repository

interface NotificationRepository {
    suspend fun getUnreadCount(userId: String?): Int
    suspend fun markAsRead(id: String)
}
