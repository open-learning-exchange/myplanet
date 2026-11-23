package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.MyLife

@Dao
interface MyLifeDao {
    fun observeByUserId(userId: String?): Flow<List<MyLife>> =
        if (userId == null) observeUnscopedByUserIdInternal() else observeByUserIdInternal(userId)

    @Query("SELECT * FROM my_life WHERE userId IS NULL OR userId = '' OR userId = '--' ORDER BY weight ASC")
    fun observeUnscopedByUserIdInternal(): Flow<List<MyLife>>

    @Query("SELECT * FROM my_life WHERE userId = :userId ORDER BY weight ASC")
    fun observeByUserIdInternal(userId: String): Flow<List<MyLife>>

    suspend fun getByUserId(userId: String?): List<MyLife> =
        if (userId == null) getUnscopedByUserIdInternal() else getByUserIdInternal(userId)

    @Query("SELECT * FROM my_life WHERE userId IS NULL OR userId = '' OR userId = '--' ORDER BY weight ASC")
    suspend fun getUnscopedByUserIdInternal(): List<MyLife>

    @Query("SELECT * FROM my_life WHERE userId = :userId ORDER BY weight ASC")
    suspend fun getByUserIdInternal(userId: String): List<MyLife>

    suspend fun getVisibleByUserId(userId: String?): List<MyLife> =
        if (userId == null) getVisibleUnscopedByUserIdInternal() else getVisibleByUserIdInternal(userId)

    @Query("SELECT * FROM my_life WHERE (userId IS NULL OR userId = '' OR userId = '--') AND isVisible = 1 ORDER BY weight ASC")
    suspend fun getVisibleUnscopedByUserIdInternal(): List<MyLife>

    @Query("SELECT * FROM my_life WHERE userId = :userId AND isVisible = 1 ORDER BY weight ASC")
    suspend fun getVisibleByUserIdInternal(userId: String): List<MyLife>

    suspend fun countByUserId(userId: String?): Int =
        if (userId == null) countUnscopedByUserIdInternal() else countByUserIdInternal(userId)

    @Query("SELECT COUNT(*) FROM my_life WHERE userId IS NULL OR userId = '' OR userId = '--'")
    suspend fun countUnscopedByUserIdInternal(): Int

    @Query("SELECT COUNT(*) FROM my_life WHERE userId = :userId")
    suspend fun countByUserIdInternal(userId: String): Int

    @Query("SELECT * FROM my_life WHERE _id IN (:ids)")
    suspend fun getByIdsInternal(ids: List<String>): List<MyLife>

    suspend fun getByIds(ids: List<String>): List<MyLife> {
        if (ids.isEmpty()) return emptyList()
        return ids.distinct().chunked(900).flatMap { getByIdsInternal(it) }
    }

    @Query("SELECT _id FROM my_life WHERE imageId = :id OR title = :id")
    suspend fun getIdsByAliasInternal(id: String): List<String>

    @Query("UPDATE my_life SET isVisible = :isVisible WHERE _id = :id")
    suspend fun updateVisibilityByIdInternal(id: String, isVisible: Boolean): Int

    @Transaction
    suspend fun updateVisibility(id: String, isVisible: Boolean) {
        if (updateVisibilityByIdInternal(id, isVisible) > 0) return
        val aliasMatches = getIdsByAliasInternal(id)
        if (aliasMatches.size == 1) {
            updateVisibilityByIdInternal(aliasMatches[0], isVisible)
        }
    }

    @Update
    suspend fun update(items: List<MyLife>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MyLife>)
}
