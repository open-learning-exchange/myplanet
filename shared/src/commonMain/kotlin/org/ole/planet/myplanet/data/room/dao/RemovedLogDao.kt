package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.ole.planet.myplanet.model.RemovedLog

@Dao
interface RemovedLogDao {
    @Query("DELETE FROM removed_log WHERE type IS :type AND userId IS :userId AND docId IS :docId")
    suspend fun deleteByTypeUserAndDoc(type: String?, userId: String?, docId: String?)

    @Query("DELETE FROM removed_log WHERE type = :type AND userId IS :userId AND docId IN (:docIds)")
    suspend fun deleteByTypeUserAndDocs(type: String, userId: String?, docIds: List<String>)

    @Transaction
    suspend fun deleteByTypeUserAndDocsChunked(type: String, userId: String?, docIds: List<String>) {
        docIds.chunked(900).forEach { chunk ->
            deleteByTypeUserAndDocs(type, userId, chunk)
        }
    }

    @Query("SELECT docId FROM removed_log WHERE type = :type AND userId IS :userId")
    suspend fun getRemovedDocIds(type: String, userId: String?): List<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RemovedLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<RemovedLog>)
}
