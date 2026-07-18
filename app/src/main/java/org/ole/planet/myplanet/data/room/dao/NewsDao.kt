package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.News

@Dao
interface NewsDao {
    @Query("SELECT * FROM news WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): News?

    @Query("SELECT * FROM news WHERE _id = :underscoreId LIMIT 1")
    suspend fun getByUnderscoreId(underscoreId: String): News?

    @Query("SELECT * FROM news")
    suspend fun getAll(): List<News>

    @Query("SELECT * FROM news WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<News>

    // Top-level posts (no reply parent), newest first. Team/community visibility is filtered
    // in-memory by the repository (mirrors the isVisibleToUser approach).
    @Query("SELECT * FROM news WHERE replyTo IS NULL OR replyTo = '' ORDER BY time DESC")
    suspend fun getTopLevel(): List<News>

    @Query("SELECT * FROM news WHERE replyTo IS NULL OR replyTo = '' ORDER BY time DESC")
    fun getTopLevelFlow(): Flow<List<News>>

    // Top-level "message" posts (community feed source).
    @Query(
        "SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') " +
            "AND docType = 'message' COLLATE NOCASE ORDER BY time DESC"
    )
    suspend fun getTopLevelMessages(): List<News>

    @Query(
        "SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') " +
            "AND docType = 'message' COLLATE NOCASE ORDER BY time DESC"
    )
    fun getTopLevelMessagesFlow(): Flow<List<News>>

    @Query("SELECT * FROM news WHERE replyTo = :newsId COLLATE NOCASE ORDER BY time DESC")
    suspend fun getReplies(newsId: String): List<News>

    @Query("SELECT * FROM news WHERE replyTo = :newsId")
    suspend fun getDirectReplies(newsId: String): List<News>

    @Query("SELECT COUNT(*) FROM news WHERE replyTo = :newsId COLLATE NOCASE")
    suspend fun getReplyCount(newsId: String): Int

    @Query(
        "SELECT * FROM news WHERE docType = 'message' COLLATE NOCASE " +
            "AND createdOn = :planetCode COLLATE NOCASE"
    )
    suspend fun getPlanetMessages(planetCode: String): List<News>

    @Query("SELECT * FROM news WHERE time >= :startTime AND time <= :endTime")
    suspend fun getInTimeRange(startTime: Long, endTime: Long): List<News>

    @Query("SELECT * FROM news WHERE time >= :startTime AND time <= :endTime AND userId = :userId")
    suspend fun getInTimeRangeForUser(startTime: Long, endTime: Long, userId: String): List<News>

    @Query("SELECT * FROM news WHERE newsId = :chatId")
    suspend fun getByNewsId(chatId: String): List<News>

    @Query("SELECT COUNT(*) FROM news WHERE viewableBy = 'teams' AND viewableId = :teamId")
    suspend fun countTeamChats(teamId: String): Long

    @Query("SELECT viewableId FROM news WHERE viewableBy = 'teams' AND viewableId IN (:teamIds)")
    suspend fun getTeamChatViewableIds(teamIds: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(news: News)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(news: List<News>)

    @Query("DELETE FROM news WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
