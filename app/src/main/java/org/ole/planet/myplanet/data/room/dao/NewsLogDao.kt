package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.NewsLog

@Dao
interface NewsLogDao {
    @Query("SELECT * FROM news_log WHERE _id IS NULL OR _id = ''")
    suspend fun getPendingUploads(): List<NewsLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: NewsLog)

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE news_log SET _id = :remoteId, _rev = :rev WHERE id = :localId")
    suspend fun markUploaded(localId: String, remoteId: String, rev: String): Int
}
