package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.ole.planet.myplanet.model.Community

@Dao
abstract class CommunityDao {
    @Query("SELECT * FROM community ORDER BY weight ASC")
    abstract suspend fun getAllSorted(): List<Community>

    @Query("DELETE FROM community")
    abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<Community>)

    @Transaction
    open suspend fun replaceAll(items: List<Community>) {
        deleteAll()
        insertAll(items)
    }
}
