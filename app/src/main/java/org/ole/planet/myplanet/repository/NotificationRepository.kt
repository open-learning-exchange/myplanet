package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmNotification

enum class NotificationFilter(val value: String) {
    ALL("all"),
    READ("read"),
    UNREAD("unread");

    companion object {
        fun fromValue(value: String?): NotificationFilter {
            val normalizedValue = value?.lowercase()?.trim()
            return values().firstOrNull { it.value == normalizedValue } ?: ALL
        }
    }
}

interface NotificationRepository {
    suspend fun getNotifications(userId: String?, filter: NotificationFilter): List<RealmNotification>
    suspend fun getUnreadNotifications(userId: String?): Int
    suspend fun getUnreadCount(userId: String?): Int
    suspend fun updateResourceNotification(userId: String?)
    suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String>
    suspend fun markAllUnreadAsRead(userId: String?): Set<String>
}
