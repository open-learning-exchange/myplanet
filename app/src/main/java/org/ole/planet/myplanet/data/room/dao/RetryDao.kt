package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.ole.planet.myplanet.model.RealmRetryOperation

/**
 * Status literals below match [RealmRetryOperation]'s STATUS_* constants (pending / in_progress /
 * completed / abandoned). Room validates the SQL at compile time.
 */
@Dao
interface RetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: RealmRetryOperation)

    @Update
    suspend fun update(operation: RealmRetryOperation)

    @Query("SELECT * FROM retry_operation WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RealmRetryOperation?

    @Query(
        "SELECT * FROM retry_operation WHERE status = 'pending' " +
            "AND nextRetryTime <= :now AND attemptCount < maxAttempts"
    )
    suspend fun getPending(now: Long): List<RealmRetryOperation>

    @Query("SELECT COUNT(*) FROM retry_operation WHERE status = 'pending' OR status = 'in_progress'")
    suspend fun getActiveCount(): Long

    @Query("DELETE FROM retry_operation WHERE status = 'completed' AND lastAttemptTime < :cutoff")
    suspend fun deleteOldCompleted(cutoff: Long)

    @Query("UPDATE retry_operation SET nextRetryTime = :now WHERE status = 'pending'")
    suspend fun resetPendingRetryTime(now: Long)

    @Query(
        "SELECT * FROM retry_operation WHERE itemId = :itemId AND uploadType = :uploadType " +
            "AND status != 'completed' AND status != 'abandoned' LIMIT 1"
    )
    suspend fun findExisting(itemId: String, uploadType: String): RealmRetryOperation?

    @Query("DELETE FROM retry_operation WHERE status = 'pending' OR status = 'abandoned'")
    suspend fun deletePendingAndAbandoned()

    @Query("UPDATE retry_operation SET status = 'pending', nextRetryTime = :retryTime WHERE status = 'in_progress'")
    suspend fun recoverStuck(retryTime: Long)
}
