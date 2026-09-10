package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.ole.planet.myplanet.model.ChatHistory

@Dao
interface ChatDao {
    // Note: ChatRepositoryImpl.sortChats owns ordering for user chats because
    // createdDate and updatedDate are string-typed, so a SQL ORDER BY would not be numerically correct.
    @Query("SELECT * FROM chat_history WHERE user = :user")
    suspend fun getByUser(user: String): List<ChatHistory>

    @Query("SELECT * FROM chat_history WHERE _id = :docId")
    suspend fun getByDocId(docId: String): List<ChatHistory>

    @Query("SELECT * FROM chat_history WHERE _id = :docId LIMIT 1")
    suspend fun findByDocId(docId: String): ChatHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChatHistory>)

    @Update
    suspend fun update(item: ChatHistory)
}
