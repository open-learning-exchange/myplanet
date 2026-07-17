package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmFeedback

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback ORDER BY openTime DESC")
    fun getAllSortedFlow(): Flow<List<RealmFeedback>>

    // `IS` so a null owner matches null rows, mirroring Realm equalTo(null).
    @Query("SELECT * FROM feedback WHERE owner IS :owner ORDER BY openTime DESC")
    fun getByOwnerFlow(owner: String?): Flow<List<RealmFeedback>>

    @Query("SELECT * FROM feedback WHERE isUploaded = 0")
    suspend fun getPending(): List<RealmFeedback>

    @Query("SELECT * FROM feedback WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RealmFeedback?

    @Query("UPDATE feedback SET status = 'Closed' WHERE id = :id")
    suspend fun closeById(id: String)

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE feedback SET isUploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RealmFeedback)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RealmFeedback>)

    @Update
    suspend fun update(item: RealmFeedback)
}
