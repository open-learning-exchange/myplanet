package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.TeamNotification

@Dao
interface TeamNotificationDao {
    @Query("UPDATE team_notification SET lastCount = :count WHERE parentId = :parentId AND type = :type")
    suspend fun updateCount(parentId: String, type: String, count: Int): Int

    @Query("SELECT * FROM team_notification WHERE type = :type AND parentId IN (:parentIds)")
    suspend fun getByTypeAndParentIdsInternal(type: String, parentIds: List<String>): List<TeamNotification>

    suspend fun getByTypeAndParentIds(type: String, parentIds: List<String>): List<TeamNotification> {
        if (parentIds.isEmpty()) return emptyList()
        return parentIds.distinct().chunked(900).flatMap { getByTypeAndParentIdsInternal(type, it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TeamNotification)
}
