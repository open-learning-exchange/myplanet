package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.ole.planet.myplanet.model.RealmTeamNotification

@Dao
interface TeamNotificationDao {
    @Query("SELECT * FROM team_notification WHERE parentId = :parentId AND type = :type LIMIT 1")
    suspend fun findByParentAndType(parentId: String, type: String): RealmTeamNotification?

    @Query("SELECT * FROM team_notification WHERE type = :type AND parentId IN (:parentIds)")
    suspend fun getByTypeAndParentIds(type: String, parentIds: List<String>): List<RealmTeamNotification>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RealmTeamNotification)

    @Update
    suspend fun update(item: RealmTeamNotification)
}
