package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import org.ole.planet.myplanet.model.AppNotification

@Dao
interface NotificationDao {
    @Query("UPDATE notifications SET isRead = 1, needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END WHERE userId IS :userId AND type = :type AND isRead = 0")
    suspend fun markSummaryAsRead(userId: String?, type: String): Int

    @Query("UPDATE notifications SET isRead = 1, needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END WHERE id = :notificationId")
    suspend fun markAsRead(notificationId: String): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE (userId = :userId OR (:isAdmin = 1 AND userId = 'SYSTEM')) AND isRead = 0")
    suspend fun getUnreadCount(userId: String, isAdmin: Boolean): Int

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AppNotification?

    @Upsert
    suspend fun upsert(notification: AppNotification)

    @Upsert
    suspend fun upsertAll(notifications: List<AppNotification>)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM notifications WHERE (userId = :userId OR (:isAdmin = 1 AND userId = 'SYSTEM')) AND message != 'INVALID' AND message != '' AND (:filter = '' OR (:filter = 'read' AND isRead = 1) OR (:filter = 'unread' AND isRead = 0)) ORDER BY isRead ASC, createdAt DESC")
    suspend fun getNotifications(userId: String, filter: String, isAdmin: Boolean): List<AppNotification>

    @Query("SELECT * FROM notifications WHERE id IN (:ids)")
    suspend fun getByIdsInternal(ids: List<String>): List<AppNotification>

    suspend fun getByIds(ids: List<String>): List<AppNotification> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(900).flatMap { chunk -> getByIdsInternal(chunk) }
    }

    @Query("SELECT id FROM notifications WHERE id IN (:ids)")
    suspend fun getIdsByIdsInternal(ids: List<String>): List<String>

    suspend fun getIdsByIds(ids: List<String>): List<String> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(900).flatMap { chunk -> getIdsByIdsInternal(chunk) }
    }

    @Query("SELECT id FROM notifications WHERE userId = :userId AND isRead = 0")
    suspend fun getUnreadIds(userId: String): List<String>

    @Query("UPDATE notifications SET isRead = 1, createdAt = :createdAt, needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END WHERE id IN (:ids)")
    suspend fun markAsReadInternal(ids: List<String>, createdAt: Long): Int

    @Transaction
    suspend fun markAsRead(ids: List<String>, createdAt: Long): Int {
        if (ids.isEmpty()) return 0
        return ids.chunked(900).sumOf { chunk -> markAsReadInternal(chunk, createdAt) }
    }

    @Query("UPDATE notifications SET isRead = 1, createdAt = :createdAt, needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END WHERE userId = :userId AND isRead = 0")
    suspend fun markAllUnreadAsRead(userId: String, createdAt: Long): Int

    @Query("SELECT * FROM notifications WHERE needsSync = 1 AND rev IS NOT NULL")
    suspend fun getPendingSyncNotifications(): List<AppNotification>

    @RawQuery
    suspend fun markSyncedNonNullRevsRaw(query: RoomRawQuery): Int

    @Query("UPDATE notifications SET needsSync = 0 WHERE id IN (:ids)")
    suspend fun markSyncedNullRevs(ids: List<String>): Int

    @Transaction
    suspend fun markSynced(syncResults: List<Pair<String, String?>>) {
        if (syncResults.isEmpty()) return

        val nullRevs = ArrayList<String>()
        val nonNullRevs = ArrayList<Pair<String, String>>()
        for ((id, rev) in syncResults) {
            if (rev != null) {
                nonNullRevs.add(id to rev)
            } else {
                nullRevs.add(id)
            }
        }

        if (nullRevs.isNotEmpty()) {
            nullRevs.chunked(900).forEach { chunk ->
                markSyncedNullRevs(chunk)
            }
        }

        if (nonNullRevs.isNotEmpty()) {
            nonNullRevs.chunked(250).forEach { chunk ->
                val whenClauses = StringBuilder()
                repeat(chunk.size) {
                    whenClauses.append(" WHEN ? THEN ?")
                }
                val inPlaceholders = chunk.joinToString(",") { "?" }
                val sql = "UPDATE notifications SET needsSync = 0, rev = CASE id$whenClauses END WHERE id IN ($inPlaceholders)"
                val query = RoomRawQuery(sql) { stmt ->
                    var index = 1
                    for ((id, rev) in chunk) {
                        stmt.bindText(index++, id)
                        stmt.bindText(index++, rev)
                    }
                    for ((id, _) in chunk) {
                        stmt.bindText(index++, id)
                    }
                }
                markSyncedNonNullRevsRaw(query)
            }
        }
    }

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteByIdsInternal(ids: List<String>): Int

    @Transaction
    suspend fun deleteByIds(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return ids.chunked(900).sumOf { chunk -> deleteByIdsInternal(chunk) }
    }
}
