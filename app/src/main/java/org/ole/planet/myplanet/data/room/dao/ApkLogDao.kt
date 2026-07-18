package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.ApkLog

@Dao
interface ApkLogDao {
    // Pending = not yet acknowledged by the server (no _rev assigned).
    @Query("SELECT * FROM apk_log WHERE _rev IS NULL")
    suspend fun getPending(): List<ApkLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ApkLog)

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE apk_log SET _rev = :rev WHERE id = :id")
    suspend fun markUploaded(id: String, rev: String): Int
}
