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
        android.util.Log.d("NotifTiming", "[${System.currentTimeMillis()}] createNotificationsBatch called: ${notifications.size} notifications for user $userId")
        if (notifications.isEmpty() || userId == null) return

        val actualUserId = userId

        // Pre-fetch all existing notifications for this user in a single query
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
        android.util.Log.d("NotifTiming", "[${System.currentTimeMillis()}] Found ${existingNotifications.size} existing notifications in DB: ${existingNotifications.keys}")

        // Filter out notifications that already exist
        val notificationsToCreate = notifications.filter { notif ->
            val key = "${notif.type}:${notif.relatedId ?: "null"}"
            !existingNotifications.containsKey(key)
        }
        android.util.Log.d("NotifTiming", "[${System.currentTimeMillis()}] After filtering: ${notificationsToCreate.size} new notifications to create")

        // Create notifications in smaller batches for better performance
        if (notificationsToCreate.isNotEmpty()) {
            val chunkSize = 10
            val chunks = notificationsToCreate.chunked(chunkSize)
            android.util.Log.d("NotifTiming", "[${System.currentTimeMillis()}] Split into ${chunks.size} chunks of max $chunkSize")

            chunks.forEachIndexed { index, chunk ->
                executeTransaction { realm ->
                    chunk.forEach { notif ->
                        realm.createObject(RealmNotification::class.java, UUID.randomUUID().toString()).apply {
                            this.userId = actualUserId
                            this.type = notif.type
                            this.message = notif.message
                            this.relatedId = notif.relatedId
                            this.createdAt = Date()
                        }
                    }
                }
                android.util.Log.d("NotifTiming", "[${System.currentTimeMillis()}] Created chunk ${index + 1}/${chunks.size} (${chunk.size} notifications)")
            }
            android.util.Log.d("NotifTiming", "[${System.currentTimeMillis()}] Created ${notificationsToCreate.size} notifications in DB")
        } else {
            android.util.Log.d("NotifTiming", "[${System.currentTimeMillis()}] No new notifications to create")
        }
    }

    override suspend fun updateResourceNotification(userId: String?, resourceCount: Int) {
        userId ?: return

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

        // Get all notifications for this user
        val allNotifications = withRealmAsync { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .findAll()
                .let { realm.copyFromRealm(it) }
        }

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
    }
}

