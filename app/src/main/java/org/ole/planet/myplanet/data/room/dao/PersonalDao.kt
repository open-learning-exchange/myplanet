package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Personal

@Dao
interface PersonalDao {
    // COLLATE NOCASE mirrors Realm's Case.INSENSITIVE title match. A null/blank userId matches any
    // user, mirroring the original conditional userId filter.
    @Query(
        "SELECT COUNT(*) FROM my_personal WHERE title = :title COLLATE NOCASE " +
            "AND (:userId IS NULL OR :userId = '' OR userId = :userId)"
    )
    suspend fun countByTitle(title: String, userId: String?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Personal)

    @Query("SELECT * FROM my_personal WHERE userId = :userId")
    fun getByUserIdFlow(userId: String): Flow<List<Personal>>

    @Query("SELECT * FROM my_personal WHERE userId = :userId AND isUploaded = 0")
    suspend fun getPendingUploads(userId: String): List<Personal>

    @Query("SELECT * FROM my_personal WHERE _id = :id LIMIT 1")
    suspend fun findByDocId(id: String): Personal?

    @Query("SELECT * FROM my_personal WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Personal?

    @Update
    suspend fun update(item: Personal)

    @Query("DELETE FROM my_personal WHERE _id = :id")
    suspend fun deleteByDocId(id: String)

    @Query("DELETE FROM my_personal WHERE id = :id")
    suspend fun deleteById(id: String)
}
