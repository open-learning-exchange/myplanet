package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.ResourceActivity

data class ResourceOpenCount(
    val title: String,
    val openCount: Int
)

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

    /**
     * Retrieves the resource title and open count for the most frequently opened resource for a given user and type.
     * Excludes rows with null or blank titles. Ties in open count are deterministically broken using title ASC.
     */
    @Query("SELECT title, COUNT(*) AS openCount FROM resource_activity WHERE user = :userName AND type = :type AND title IS NOT NULL AND TRIM(title) != '' GROUP BY resourceId ORDER BY openCount DESC, title ASC LIMIT 1")
    suspend fun getMostOpenedResource(userName: String, type: String): ResourceOpenCount?

    @Query("SELECT * FROM resource_activity WHERE user = :userName AND type = :type")
    fun observeByUserAndType(userName: String, type: String): Flow<List<ResourceActivity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ResourceActivity)

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE resource_activity SET _id = :remoteId, _rev = :rev WHERE id = :localId")
    suspend fun markUploaded(localId: String, remoteId: String, rev: String): Int
}
