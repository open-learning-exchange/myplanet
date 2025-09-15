package org.ole.planet.myplanet.repository

import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNotification

class NotificationRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), NotificationRepository {

    override suspend fun getUnreadCount(userId: String?): Int {
        if (userId == null) return 0

        return count(RealmNotification::class.java) {
            equalTo("userId", userId)
            equalTo("isRead", false)
        }.toInt()
    }

    override suspend fun updateResourceNotification(userId: String?) {
        userId ?: return

        val resourceCount = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }.count { it.needToUpdate() && it.userId?.contains(userId) == true }

        val existingNotification = findByField(RealmNotification::class.java, "userId", userId)
            ?.takeIf { it.type == "resource" }

        if (resourceCount > 0) {
            val notification = existingNotification?.apply {
                message = "$resourceCount"
                relatedId = "$resourceCount"
            } ?: RealmNotification().apply {
                this.userId = userId
                this.type = "resource"
                this.message = "$resourceCount"
                this.relatedId = "$resourceCount"
                this.createdAt = Date()
            }
            save(notification)
        } else {
            existingNotification?.let { delete(RealmNotification::class.java, "id", it.id) }
        }
    }

    override suspend fun getNotifications(userId: String, filter: String): List<RealmNotification> {
        return queryList(RealmNotification::class.java) {
            equalTo("userId", userId)
            when (filter) {
                "read" -> equalTo("isRead", true)
                "unread" -> equalTo("isRead", false)
            }
            sort("createdAt", Sort.DESCENDING)
        }.filter { it.message.isNotEmpty() && it.message != "INVALID" }
    }

    override suspend fun markAsRead(notificationId: String) {
        update(RealmNotification::class.java, "id", notificationId) { it.isRead = true }
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

