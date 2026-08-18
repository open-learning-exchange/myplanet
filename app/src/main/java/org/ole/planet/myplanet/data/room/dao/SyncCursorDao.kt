package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.SyncCursor

@Dao
interface SyncCursorDao {
    @Query("SELECT since FROM sync_cursors WHERE tableName = :table")
    suspend fun getSince(table: String): String?

    @Upsert
    suspend fun upsert(cursor: SyncCursor)

    @Query("DELETE FROM sync_cursors WHERE tableName = :table")
    suspend fun clear(table: String)
}
