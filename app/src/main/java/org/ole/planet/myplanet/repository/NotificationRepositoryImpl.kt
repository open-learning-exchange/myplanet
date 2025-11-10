package org.ole.planet.myplanet.repository

import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNotification

class NotificationRepositoryImpl @Inject constructor(
        databaseService: DatabaseService,
    ) : RealmRepository(databaseService), NotificationRepository {

    override suspend fun createNotificationIfMissing(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    ) {
        val actualUserId = userId ?: ""
        executeTransaction { realm ->
            val query = realm.where(RealmNotification::class.java)
                .equalTo("userId", actualUserId)
                .equalTo("type", type)

            val existingNotification =
                if (relatedId != null) {
                    query.equalTo("relatedId", relatedId).findFirst()
                } else {
                    query.isNull("relatedId").findFirst()
                }

            if (existingNotification == null) {
                realm.createObject(RealmNotification::class.java, UUID.randomUUID().toString()).apply {
                    this.userId = actualUserId
                    this.type = type
                    this.message = message
                    this.relatedId = relatedId
                    this.createdAt = Date()
                }
            }
        }
    }

    override suspend fun createNotificationsIfMissing(
        notifications: List<NotificationRepository.NotificationData>,
        userId: String?,
    ) {
        val actualUserId = userId ?: ""
        if (notifications.isEmpty()) return

        val startTime = System.currentTimeMillis()
        android.util.Log.d("NotificationRepository", "createNotificationsIfMissing: Starting batch create for ${notifications.size} notifications")

        executeTransaction { realm ->
            // OPTIMIZATION: Query only UNREAD notifications (old read ones don't matter)
            // This is much faster than searching through all historical notifications
            val queryStartTime = System.currentTimeMillis()
            val existingUnreadNotifications = realm.where(RealmNotification::class.java)
                .equalTo("userId", actualUserId)
                .equalTo("isRead", false)
                .findAll()

            android.util.Log.d("NotificationRepository", "createNotificationsIfMissing: Found ${existingUnreadNotifications.size} unread notifications in ${System.currentTimeMillis() - queryStartTime}ms")

            // Create a set of existing notification keys for O(1) lookup
            val existingKeys = existingUnreadNotifications.mapNotNull { existing ->
                if (existing.relatedId != null) {
                    "${existing.type}:${existing.relatedId}"
                } else {
                    "${existing.type}:null"
                }
            }.toSet()

            val now = Date()
            var createdCount = 0
            var skippedCount = 0

            // Batch create all missing notifications in a single transaction
            notifications.forEach { notification ->
                val key = if (notification.relatedId != null) {
                    "${notification.type}:${notification.relatedId}"
                } else {
                    "${notification.type}:null"
                }

                if (key !in existingKeys) {
                    realm.createObject(RealmNotification::class.java, UUID.randomUUID().toString()).apply {
                        this.userId = actualUserId
                        this.type = notification.type
                        this.message = notification.message
                        this.relatedId = notification.relatedId
                        this.createdAt = now
                    }
                    createdCount++
                } else {
                    skippedCount++
                }
            }

            val endTime = System.currentTimeMillis()
            android.util.Log.d("NotificationRepository", "createNotificationsIfMissing: Created $createdCount new, skipped $skippedCount existing in ${endTime - startTime}ms total")
        }
    }

    override suspend fun getUnreadCount(userId: String?): Int {
        if (userId == null) return 0

        return count(RealmNotification::class.java) {
            equalTo("userId", userId)
            equalTo("isRead", false)
        }.toInt()
    }

    override suspend fun updateResourceNotification(userId: String?, resourceCount: Int) {
        userId ?: return

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
                    notification.isRead = true
                    notification.createdAt = Date()
                    updatedIds.add(notification.id)
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
                    notification.isRead = true
                    notification.createdAt = now
                    updatedIds.add(notification.id)
                }
        }
        return updatedIds
    }

    override suspend fun getNotifications(userId: String, filter: String): List<RealmNotification> {
        return queryList(RealmNotification::class.java) {
            equalTo("userId", userId)
            when (filter) {
                "read" -> equalTo("isRead", true)
                "unread" -> equalTo("isRead", false)
            }
            sort("isRead", io.realm.Sort.ASCENDING, "createdAt", io.realm.Sort.DESCENDING)
        }.filter {
            it.message.isNotEmpty() && it.message != "INVALID"
        }
    }
}

