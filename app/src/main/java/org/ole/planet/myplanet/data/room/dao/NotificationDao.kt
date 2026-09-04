package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import java.util.Date
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
    suspend fun getByIds(ids: List<String>): List<AppNotification>

    @Query("SELECT id FROM notifications WHERE id IN (:ids)")
    suspend fun getIdsByIds(ids: List<String>): List<String>

    @Query("SELECT id FROM notifications WHERE userId = :userId AND isRead = 0")
    suspend fun getUnreadIds(userId: String): List<String>

    @Query("UPDATE notifications SET isRead = 1, createdAt = :createdAt, needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END WHERE id IN (:ids)")
    suspend fun markAsRead(ids: List<String>, createdAt: Date): Int

    @Query("UPDATE notifications SET isRead = 1, createdAt = :createdAt, needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END WHERE userId = :userId AND isRead = 0")
    suspend fun markAllUnreadAsRead(userId: String, createdAt: Date): Int

    @Query("SELECT * FROM notifications WHERE needsSync = 1 AND rev IS NOT NULL")
    suspend fun getPendingSyncNotifications(): List<AppNotification>

    @Transaction
    suspend fun markSynced(syncResults: List<Pair<String, String?>>) {
        val nonNullRevs = syncResults.filter { it.second != null }
        if (nonNullRevs.isNotEmpty()) {
            nonNullRevs.chunked(450).forEach { chunk ->
                val queryBuilder = StringBuilder("UPDATE notifications SET needsSync = 0, rev = CASE id ")
                val args = mutableListOf<Any>()

                chunk.forEach { (id, rev) ->
                    queryBuilder.append("WHEN ? THEN ? ")
                    args.add(id)
                    args.add(rev!!)
                }

                queryBuilder.append("ELSE rev END WHERE id IN (")
                chunk.forEachIndexed { index, pair ->
                    if (index > 0) queryBuilder.append(", ")
                    queryBuilder.append("?")
                    args.add(pair.first)
                }
                queryBuilder.append(")")

                executeRawUpdate(SimpleSQLiteQuery(queryBuilder.toString(), args.toTypedArray()))
            }
        }

        val nullRevs = syncResults.filter { it.second == null }.map { it.first }
        if (nullRevs.isNotEmpty()) {
            nullRevs.chunked(900).forEach {
                markSyncedWithoutRev(it)
            }
        }
    }

    @RawQuery
    suspend fun executeRawUpdate(query: SupportSQLiteQuery): Int

    @Query("UPDATE notifications SET needsSync = 0 WHERE id IN (:ids)")
    suspend fun markSyncedWithoutRev(ids: List<String>)

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int
}
