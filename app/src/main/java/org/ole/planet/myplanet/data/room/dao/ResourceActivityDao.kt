package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.ResourceActivity

@Dao
interface ResourceActivityDao {
    @Query("SELECT * FROM resource_activity WHERE _rev IS NULL AND type != 'sync'")
    suspend fun getPendingUploads(): List<ResourceActivity>

    @Query("SELECT * FROM resource_activity WHERE _rev IS NULL AND type = 'sync'")
    suspend fun getPendingSyncUploads(): List<ResourceActivity>

    @Query("SELECT * FROM resource_activity WHERE user = :userName AND type = :type")
    suspend fun getByUserAndType(userName: String, type: String): List<ResourceActivity>

    @Query("SELECT COUNT(*) FROM resource_activity WHERE user = :userName AND type = :type")
    suspend fun countByUserAndType(userName: String, type: String): Long

    @Query("SELECT * FROM resource_activity WHERE user = :userName AND type = :type")
    fun observeByUserAndType(userName: String, type: String): Flow<List<ResourceActivity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ResourceActivity)

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE resource_activity SET _id = :remoteId, _rev = :rev WHERE id = :localId")
    suspend fun markUploaded(localId: String, remoteId: String, rev: String): Int
}
