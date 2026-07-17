package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.ole.planet.myplanet.model.RealmRating

@Dao
interface RatingDao {
    @Query("SELECT * FROM rating WHERE type IS :type")
    suspend fun getByType(type: String?): List<RealmRating>

    @Query("SELECT * FROM rating WHERE type IS :type AND item IS :item")
    suspend fun getByTypeAndItem(type: String?, item: String?): List<RealmRating>

    @Query("SELECT * FROM rating WHERE type = :type AND userId = :userId AND item = :item LIMIT 1")
    suspend fun findByTypeUserItem(type: String, userId: String, item: String): RealmRating?

    @Query("SELECT * FROM rating WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RealmRating?

    // Pending upload = edited locally, excluding guest users (mirrors filterGuests).
    @Query("SELECT * FROM rating WHERE isUpdated = 1 AND (userId IS NULL OR userId NOT LIKE 'guest%')")
    suspend fun getPendingUploads(): List<RealmRating>

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE rating SET isUpdated = 0 WHERE id = :id")
    suspend fun markUploaded(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RealmRating)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RealmRating>)

    @Update
    suspend fun update(item: RealmRating)
}
