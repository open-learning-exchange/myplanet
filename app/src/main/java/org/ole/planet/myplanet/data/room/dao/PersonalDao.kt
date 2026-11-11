package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Personal

@Dao
interface PersonalDao {
    @Query("SELECT COUNT(*) FROM my_personal WHERE title = :title COLLATE NOCASE")
    suspend fun countByTitle(title: String): Int

    @Query("SELECT COUNT(*) FROM my_personal WHERE title = :title COLLATE NOCASE AND userId = :userId")
    suspend fun countByTitleAndUserId(title: String, userId: String): Int

    // COLLATE NOCASE mirrors Realm's Case.INSENSITIVE title match. A null/empty userId matches any
    // user, mirroring the original conditional userId filter.
    suspend fun countByTitle(title: String, userId: String?): Int {
        return if (userId.isNullOrEmpty()) {
            countByTitle(title)
        } else {
            countByTitleAndUserId(title, userId)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Personal)

    @Query("SELECT * FROM my_personal WHERE userId = :userId ORDER BY date DESC, title COLLATE NOCASE ASC")
    fun getByUserIdFlow(userId: String): Flow<List<Personal>>

    @Query("SELECT * FROM my_personal WHERE _id = :id LIMIT 1")
    suspend fun findByDocId(id: String): Personal?

    @Query("SELECT * FROM my_personal WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Personal?

    @Query("DELETE FROM my_personal WHERE _id = :id OR id = :id")
    suspend fun deleteByIdOrDocId(id: String)

    @Query("UPDATE my_personal SET isUploaded = 1, _id = :newId, _rev = :rev WHERE id = :id")
    suspend fun updateUploadedStatus(id: String, newId: String, rev: String)

    @Query("UPDATE my_personal SET _id = :newId, _rev = :rev WHERE id = :id")
    suspend fun updateRemoteDocRef(id: String, newId: String, rev: String)

    @Query("UPDATE my_personal SET title = COALESCE(:title, title), description = COALESCE(:description, description) WHERE _id = :id OR id = :id")
    suspend fun updateFields(id: String, title: String?, description: String?)
}
