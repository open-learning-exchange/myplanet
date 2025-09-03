package org.ole.planet.myplanet.repository

import io.realm.Realm

interface NotificationRepository {
    suspend fun getUnreadCount(userId: String?): Int

    suspend fun updateResourceNotification(userId: String?)

    fun createNotificationIfNotExists(
        realm: Realm,
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    )
}
