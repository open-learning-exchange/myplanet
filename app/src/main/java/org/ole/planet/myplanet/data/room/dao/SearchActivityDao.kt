package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.SearchActivity

@Dao
interface SearchActivityDao {
    @Query("SELECT * FROM search_activity WHERE _rev = ''")
    suspend fun getPendingUploads(): List<SearchActivity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: SearchActivity)

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE search_activity SET _id = :remoteId, _rev = :rev WHERE id = :localId")
    suspend fun markUploaded(localId: String, remoteId: String, rev: String): Int
}
