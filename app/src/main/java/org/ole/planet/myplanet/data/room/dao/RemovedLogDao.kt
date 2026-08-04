package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.RemovedLog

@Dao
interface RemovedLogDao {
    @Query("DELETE FROM removed_log WHERE type = :type AND userId = :userId AND docId = :docId")
    suspend fun deleteByTypeUserAndDoc(type: String?, userId: String?, docId: String?)

    @Query("DELETE FROM removed_log WHERE type = :type AND userId = :userId AND docId IN (:docIds)")
    suspend fun deleteByTypeUserAndDocs(type: String, userId: String?, docIds: List<String>)

    @Query("SELECT docId FROM removed_log WHERE type = :type AND userId = :userId")
    suspend fun getRemovedDocIds(type: String, userId: String?): List<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RemovedLog)
}
