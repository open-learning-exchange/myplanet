package org.ole.planet.myplanet.data.room.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.ole.planet.myplanet.model.ApkLog

@Dao
interface ApkLogDao {
    // Pending = not yet acknowledged by the server (no _rev assigned).
    @Query("SELECT * FROM apk_log WHERE _rev IS NULL")
    suspend fun getPending(): List<ApkLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ApkLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ApkLog>)

    @Query("SELECT id FROM apk_log WHERE id IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<String>

    @Update(entity = ApkLog::class)
    suspend fun markUploadedBatchInternal(updates: List<UploadUpdate>)

    @Transaction
    suspend fun markUploadedBatch(updates: List<UploadUpdate>): Set<String> {
        if (updates.isEmpty()) return emptySet()
        val ids = updates.map { it.id }
        val existingIds = ids.chunked(900).flatMap { chunk -> getExistingIds(chunk) }.toSet()
        markUploadedBatchInternal(updates)
        return ids.filterNot { it in existingIds }.toSet()
    }

    data class UploadUpdate(
        @ColumnInfo(name = "id") val id: String,
        @ColumnInfo(name = "_rev") val rev: String
    )
}
