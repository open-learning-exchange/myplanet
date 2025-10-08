package org.ole.planet.myplanet.repository

import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmMyLibrary

class NotificationRepositoryImpl @Inject constructor(
        databaseService: DatabaseService,
    ) : RealmRepository(databaseService), NotificationRepository {

    override suspend fun getNotifications(userId: String?, filter: NotificationFilter): List<RealmNotification> {
        val actualUserId = userId ?: return emptyList()

        val notifications = queryList(RealmNotification::class.java) {
            equalTo("userId", actualUserId)
            when (filter) {
                NotificationFilter.READ -> equalTo("isRead", true)
                NotificationFilter.UNREAD -> equalTo("isRead", false)
                NotificationFilter.ALL -> {
                    // no-op
                }
            }
        }

        return notifications
            .filter { it.message.isNotEmpty() && it.message != "INVALID" }
            .sortedWith(compareBy<RealmNotification> { it.isRead }.thenByDescending { it.createdAt })
    }

    override suspend fun getUnreadNotifications(userId: String?): Int {
        val actualUserId = userId ?: return 0

        return count(RealmNotification::class.java) {
            equalTo("userId", actualUserId)
            equalTo("isRead", false)
        }.toInt()
    }

    override suspend fun getUnreadCount(userId: String?): Int {
        return getUnreadNotifications(userId)
    }

    override suspend fun updateResourceNotification(userId: String?) {
        userId ?: return

        val resourceCount = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }.count { it.needToUpdate() && it.userId?.contains(userId) == true }

        val notificationId = "$userId:resource:count"
        val existingNotification = findByField(RealmNotification::class.java, "id", notificationId)

        if (resourceCount > 0) {
            val previousCount = existingNotification?.message?.toIntOrNull() ?: 0
            val countChanged = previousCount != resourceCount

            val notification = existingNotification?.apply {
                message = "$resourceCount"
                relatedId = "$resourceCount"
                if (countChanged) {
                    this.isRead = false
                    this.createdAt = Date()
                }
            } ?: RealmNotification().apply {
                this.id = notificationId
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

    override suspend fun markNotificationsAsRead(notificationIds: Set<String>): Set<String> {
        if (notificationIds.isEmpty()) return emptySet()

        val updatedIds = mutableSetOf<String>()
        executeTransaction { realm ->
            realm.where(RealmNotification::class.java)
                .`in`("id", notificationIds.toTypedArray())
                .findAll()
                ?.forEach { notification ->
                    if (!notification.isRead) {
                        notification.isRead = true
                        notification.createdAt = Date()
                        updatedIds.add(notification.id)
                    }
                }
        }
        return updatedIds
    }

    override suspend fun markAllUnreadAsRead(userId: String?): Set<String> {
        val actualUserId = userId ?: return emptySet()
        val updatedIds = mutableSetOf<String>()
        val now = Date()
        executeTransaction { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", actualUserId)
                .equalTo("isRead", false)
                .findAll()
                ?.forEach { notification ->
                    if (!notification.isRead) {
                        notification.isRead = true
                        notification.createdAt = now
                        updatedIds.add(notification.id)
                    }
                }
        }
        return updatedIds
    }
}

