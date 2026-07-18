package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import java.util.Date
import org.ole.planet.myplanet.model.AppNotification

@Dao
interface NotificationDao {
    @Query("UPDATE notifications SET isRead = 1, needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END WHERE userId = :userId AND type = :type AND isRead = 0")
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

    @Query("UPDATE notifications SET isRead = 1, createdAt = :createdAt, needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END WHERE id IN (:ids)")
    suspend fun markAsRead(ids: List<String>, createdAt: Date): Int

    @Query("UPDATE notifications SET isRead = 1, createdAt = :createdAt, needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END WHERE userId = :userId AND isRead = 0")
    suspend fun markAllUnreadAsRead(userId: String, createdAt: Date): Int

    @Query("SELECT * FROM notifications WHERE needsSync = 1 AND rev IS NOT NULL")
    suspend fun getPendingSyncNotifications(): List<AppNotification>

    @Query("UPDATE notifications SET needsSync = 0, rev = COALESCE(:rev, rev) WHERE id = :id")
    suspend fun markSynced(id: String, rev: String?)

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int
}
