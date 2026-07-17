package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.ole.planet.myplanet.model.RealmCommunity

@Dao
abstract class CommunityDao {
    @Query("SELECT * FROM community ORDER BY weight ASC")
    abstract suspend fun getAllSorted(): List<RealmCommunity>

    @Query("DELETE FROM community")
    abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<RealmCommunity>)

    @Transaction
    open suspend fun replaceAll(items: List<RealmCommunity>) {
        deleteAll()
        insertAll(items)
    }
}
