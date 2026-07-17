package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.ole.planet.myplanet.model.RealmChatHistory

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_history WHERE user = :user ORDER BY id DESC")
    suspend fun getByUser(user: String): List<RealmChatHistory>

    @Query("SELECT * FROM chat_history WHERE _id = :docId")
    suspend fun getByDocId(docId: String): List<RealmChatHistory>

    @Query("SELECT * FROM chat_history WHERE _id = :docId LIMIT 1")
    suspend fun findByDocId(docId: String): RealmChatHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RealmChatHistory>)

    @Update
    suspend fun update(item: RealmChatHistory)
}
