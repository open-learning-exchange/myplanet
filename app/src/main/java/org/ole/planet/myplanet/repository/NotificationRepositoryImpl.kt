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

    override suspend fun getUnreadCount(userId: String?): Int {
        if (userId == null) return 0

        return count(RealmNotification::class.java) {
            equalTo("userId", userId)
            equalTo("isRead", false)
        }.toInt()
    }

    override suspend fun createNotificationsBatch(
        notifications: List<NotificationData>,
        userId: String?
    ) {
        if (notifications.isEmpty() || userId == null) return

        android.util.Log.d("NotificationFlow", "NotificationRepository.createNotificationsBatch() - Creating ${notifications.size} notifications in single transaction")
        val startTime = System.currentTimeMillis()

        val actualUserId = userId

        // Pre-fetch all existing notifications for this user in a single query
        val preFetchStartTime = System.currentTimeMillis()
        val existingNotifications = withRealmAsync { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", actualUserId)
                .findAll()
                .map { notif ->
                    // Create a unique key for fast lookup
                    val key = "${notif.type}:${notif.relatedId ?: "null"}"
                    key to notif
                }
                .toMap()
        }
        android.util.Log.d("NotificationFlow", "NotificationRepository.createNotificationsBatch() - Pre-fetched ${existingNotifications.size} existing notifications in ${System.currentTimeMillis() - preFetchStartTime}ms")

        // Filter out notifications that already exist
        val filterStartTime = System.currentTimeMillis()
        val notificationsToCreate = notifications.filter { notif ->
            val key = "${notif.type}:${notif.relatedId ?: "null"}"
            !existingNotifications.containsKey(key)
        }
        android.util.Log.d("NotificationFlow", "NotificationRepository.createNotificationsBatch() - Filtered to ${notificationsToCreate.size} new notifications in ${System.currentTimeMillis() - filterStartTime}ms")

        // Create only the new notifications in a single transaction
        if (notificationsToCreate.isNotEmpty()) {
            val createStartTime = System.currentTimeMillis()
            executeTransaction { realm ->
                notificationsToCreate.forEach { notif ->
                    realm.createObject(RealmNotification::class.java, UUID.randomUUID().toString()).apply {
                        this.userId = actualUserId
                        this.type = notif.type
                        this.message = notif.message
                        this.relatedId = notif.relatedId
                        this.createdAt = Date()
                    }
                }
            }
            android.util.Log.d("NotificationFlow", "NotificationRepository.createNotificationsBatch() - Created ${notificationsToCreate.size} new notifications in transaction in ${System.currentTimeMillis() - createStartTime}ms")
        } else {
            android.util.Log.d("NotificationFlow", "NotificationRepository.createNotificationsBatch() - No new notifications to create (all already exist)")
        }

        android.util.Log.d("NotificationFlow", "NotificationRepository.createNotificationsBatch() - COMPLETED in ${System.currentTimeMillis() - startTime}ms")
    }

    override suspend fun updateResourceNotification(userId: String?, resourceCount: Int) {
        userId ?: return

        android.util.Log.d("NotificationFlow", "NotificationRepository.updateResourceNotification() - START - count=$resourceCount")
        val startTime = System.currentTimeMillis()

        val notificationId = "$userId:resource:count"

        executeTransaction { realm ->
            val existingNotification = realm.where(RealmNotification::class.java)
                .equalTo("id", notificationId)
                .findFirst()

            if (resourceCount > 0) {
                val previousCount = existingNotification?.message?.toIntOrNull() ?: 0
                val countChanged = previousCount != resourceCount

                if (existingNotification != null) {
                    existingNotification.message = "$resourceCount"
                    existingNotification.relatedId = "$resourceCount"
                    if (countChanged) {
                        existingNotification.isRead = false
                        existingNotification.createdAt = Date()
                    }
                } else {
                    realm.createObject(RealmNotification::class.java, notificationId).apply {
                        this.userId = userId
                        this.type = "resource"
                        this.message = "$resourceCount"
                        this.relatedId = "$resourceCount"
                        this.createdAt = Date()
                    }
                }
            } else {
                existingNotification?.deleteFromRealm()
            }
        }

        android.util.Log.d("NotificationFlow", "NotificationRepository.updateResourceNotification() - COMPLETED in ${System.currentTimeMillis() - startTime}ms")
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

    override suspend fun cleanupDuplicateNotifications(userId: String?) {
        if (userId == null) return

        android.util.Log.d("NotificationFlow", "NotificationRepository.cleanupDuplicateNotifications() - START - userId=$userId")
        val startTime = System.currentTimeMillis()

        // Get all notifications for this user
        val allNotifications = withRealmAsync { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .findAll()
                .let { realm.copyFromRealm(it) }
        }

        android.util.Log.d("NotificationFlow", "NotificationRepository.cleanupDuplicateNotifications() - Found ${allNotifications.size} total notifications")

        // Group by type+relatedId, keep only the most recent one
        val notificationsToKeep = allNotifications
            .groupBy { "${it.type}:${it.relatedId ?: "null"}" }
            .mapValues { (_, notifications) ->
                // Keep the most recent one (latest createdAt)
                notifications.maxByOrNull { it.createdAt?.time ?: 0L }
            }
            .values
            .filterNotNull()

        val idsToKeep = notificationsToKeep.map { it.id }.toSet()
        val idsToDelete = allNotifications.map { it.id }.filter { !idsToKeep.contains(it) }

        android.util.Log.d("NotificationFlow", "NotificationRepository.cleanupDuplicateNotifications() - Keeping ${notificationsToKeep.size} unique notifications, deleting ${idsToDelete.size} duplicates")

        if (idsToDelete.isNotEmpty()) {
            executeTransaction { realm ->
                idsToDelete.forEach { idToDelete ->
                    realm.where(RealmNotification::class.java)
                        .equalTo("id", idToDelete)
                        .findFirst()
                        ?.deleteFromRealm()
                }
            }
        }

        android.util.Log.d("NotificationFlow", "NotificationRepository.cleanupDuplicateNotifications() - COMPLETED in ${System.currentTimeMillis() - startTime}ms")
    }
}

