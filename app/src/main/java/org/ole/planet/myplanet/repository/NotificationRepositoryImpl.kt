package org.ole.planet.myplanet.repository

import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNotification

class NotificationRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), NotificationRepository {

    override suspend fun getUnreadCount(userId: String?): Int {
        return withRealm { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .count()
                .toInt()
        }
    }

    override suspend fun getNotifications(userId: String, filter: String): List<RealmNotification> {
        return withRealm { realm ->
            val query = realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
            when (filter) {
                "read" -> query.equalTo("isRead", true)
                "unread" -> query.equalTo("isRead", false)
            }
            val results = query.sort("createdAt", Sort.DESCENDING).findAll()
            realm.copyFromRealm(results).filter {
                it.message.isNotEmpty() && it.message != "INVALID"
            }
        }
    }

    override suspend fun markAsRead(notificationId: String) {
        executeTransaction { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("id", notificationId)
                .findFirst()?.isRead = true
        }
    }

    override suspend fun markAllAsRead(userId: String) {
        executeTransaction { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .findAll()
                .forEach { it.isRead = true }
        }
    }
}
