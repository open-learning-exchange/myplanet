package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.ole.planet.myplanet.model.Rating
import org.ole.planet.myplanet.model.RatingPromptLog

data class RatingAggregate(
    val totalCount: Int,
    val averageRate: Double?,
)

@Dao
interface RatingDao {
    @Query("SELECT * FROM rating WHERE type IS :type")
    suspend fun getByType(type: String?): List<Rating>

    // Aggregates the count and average rate directly in SQLite so a summary no longer has to
    // load and reduce every rating row in Kotlin.
    @Query(
        "SELECT COUNT(*) AS totalCount, AVG(rate) AS averageRate " +
            "FROM rating WHERE type IS :type AND item IS :item"
    )
    suspend fun getAggregate(type: String?, item: String?): RatingAggregate

    @Query("SELECT * FROM rating WHERE type = :type AND userId = :userId AND item = :item LIMIT 1")
    suspend fun findByTypeUserItem(type: String, userId: String, item: String): Rating?

    @Query("SELECT * FROM rating WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Rating?

    // Pending upload = edited locally, excluding guest users (mirrors filterGuests).
    @Query("SELECT * FROM rating WHERE isUpdated = 1 AND (userId IS NULL OR userId NOT LIKE 'guest%')")
    suspend fun getPendingUploads(): List<Rating>

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE rating SET isUpdated = 0 WHERE id = :id")
    suspend fun markUploaded(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Rating)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Rating>)

    @Update
    suspend fun update(item: Rating)

    @Query("SELECT EXISTS(SELECT 1 FROM rating_prompt_log WHERE userId = :userId AND item = :item AND type = :type)")
    suspend fun isRatingPrompted(userId: String, item: String, type: String = "resource"): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRatingPrompted(prompt: RatingPromptLog)
}
