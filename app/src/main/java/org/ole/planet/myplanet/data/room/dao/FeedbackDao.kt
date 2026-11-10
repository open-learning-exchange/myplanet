package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Feedback

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback ORDER BY openTime DESC")
    fun getAllSortedFlow(): Flow<List<Feedback>>

    @Query("SELECT * FROM feedback WHERE owner IS :owner ORDER BY openTime DESC")
    fun getByOwnerFlow(owner: String?): Flow<List<Feedback>>

    @Query("SELECT * FROM feedback WHERE isUploaded = 0")
    suspend fun getPending(): List<Feedback>

    @Query("SELECT * FROM feedback WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Feedback?

    @Query("SELECT * FROM feedback WHERE id IN (:ids)")
    suspend fun getByIdsInternal(ids: List<String>): List<Feedback>

    suspend fun getByIds(ids: List<String>): List<Feedback> {
        if (ids.isEmpty()) return emptyList()
        return ids.distinct().chunked(900).flatMap { chunk -> getByIdsInternal(chunk) }
    }

    @Query("SELECT * FROM feedback WHERE _id IN (:remoteIds)")
    suspend fun getByRemoteIdsInternal(remoteIds: List<String>): List<Feedback>

    suspend fun getByRemoteIds(remoteIds: List<String>): List<Feedback> {
        if (remoteIds.isEmpty()) return emptyList()
        return remoteIds.distinct().chunked(900).flatMap { chunk -> getByRemoteIdsInternal(chunk) }
    }

    @Query("DELETE FROM feedback WHERE id IN (:ids)")
    suspend fun deleteByIdsInternal(ids: List<String>)

    suspend fun deleteByIds(ids: List<String>) {
        ids.distinct().chunked(900).forEach { chunk -> deleteByIdsInternal(chunk) }
    }

    @Query("UPDATE feedback SET status = 'Closed', isUploaded = 0 WHERE id = :id")
    suspend fun closeById(id: String)

    @Query("UPDATE feedback SET isUploaded = 1, _id = COALESCE(NULLIF(:remoteId, ''), _id), _rev = COALESCE(NULLIF(:remoteRev, ''), _rev) WHERE id = :id")
    suspend fun markUploaded(id: String, remoteId: String, remoteRev: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Feedback)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Feedback>)

    @Update
    suspend fun update(item: Feedback)
}
