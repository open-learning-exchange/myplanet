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
    @Query("SELECT * FROM my_life WHERE (:userId IS NULL AND (userId IS NULL OR userId = '' OR userId = '--')) OR (:userId IS NOT NULL AND userId = :userId) ORDER BY weight ASC")
    fun observeByUserId(userId: String?): Flow<List<MyLife>>

    @Query("SELECT * FROM my_life WHERE (:userId IS NULL AND (userId IS NULL OR userId = '' OR userId = '--')) OR (:userId IS NOT NULL AND userId = :userId) ORDER BY weight ASC")
    suspend fun getByUserId(userId: String?): List<MyLife>

    @Query("SELECT * FROM my_life WHERE ((:userId IS NULL AND (userId IS NULL OR userId = '' OR userId = '--')) OR (:userId IS NOT NULL AND userId = :userId)) AND isVisible = 1 ORDER BY weight ASC")
    suspend fun getVisibleByUserId(userId: String?): List<MyLife>

    @Query("SELECT COUNT(*) FROM my_life WHERE (:userId IS NULL AND (userId IS NULL OR userId = '' OR userId = '--')) OR (:userId IS NOT NULL AND userId = :userId)")
    suspend fun countByUserId(userId: String?): Int

    @Query("SELECT * FROM my_life WHERE _id IN (:ids)")
    suspend fun getByIdsInternal(ids: List<String>): List<MyLife>

    suspend fun getByIds(ids: List<String>): List<MyLife> {
        if (ids.isEmpty()) return emptyList()
        return ids.distinct().chunked(900).flatMap { getByIdsInternal(it) }
    }

    @Query("UPDATE my_life SET isVisible = :isVisible WHERE _id = :id")
    suspend fun updateVisibilityById(id: String, isVisible: Boolean): Int

    @Query("SELECT _id FROM my_life WHERE imageId = :alias OR title = :alias LIMIT 2")
    suspend fun getIdsByAlias(alias: String): List<String>

    // Exact _id first; an imageId/title alias only counts when it names a single row,
    // so a tile label shared across users never flips another user's row.
    @Transaction
    suspend fun updateVisibility(id: String, isVisible: Boolean) {
        if (updateVisibilityById(id, isVisible) > 0) return
        getIdsByAlias(id).singleOrNull()?.let { updateVisibilityById(it, isVisible) }
    }

    @Update
    suspend fun update(items: List<MyLife>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MyLife>)
}
