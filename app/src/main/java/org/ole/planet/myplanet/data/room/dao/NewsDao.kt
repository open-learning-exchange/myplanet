package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmNews

@Dao
interface NewsDao {
    @Query("SELECT * FROM news WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RealmNews?

    @Query("SELECT * FROM news WHERE _id = :underscoreId LIMIT 1")
    suspend fun getByUnderscoreId(underscoreId: String): RealmNews?

    @Query("SELECT * FROM news")
    suspend fun getAll(): List<RealmNews>

    @Query("SELECT * FROM news WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<RealmNews>

    // Top-level posts (no reply parent), newest first. Team/community visibility is filtered
    // in-memory by the repository (mirrors the isVisibleToUser approach).
    @Query("SELECT * FROM news WHERE replyTo IS NULL OR replyTo = '' ORDER BY time DESC")
    suspend fun getTopLevel(): List<RealmNews>

    @Query("SELECT * FROM news WHERE replyTo IS NULL OR replyTo = '' ORDER BY time DESC")
    fun getTopLevelFlow(): Flow<List<RealmNews>>

    // Top-level "message" posts (community feed source).
    @Query(
        "SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') " +
            "AND docType = 'message' COLLATE NOCASE ORDER BY time DESC"
    )
    suspend fun getTopLevelMessages(): List<RealmNews>

    @Query(
        "SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') " +
            "AND docType = 'message' COLLATE NOCASE ORDER BY time DESC"
    )
    fun getTopLevelMessagesFlow(): Flow<List<RealmNews>>

    @Query("SELECT * FROM news WHERE replyTo = :newsId COLLATE NOCASE ORDER BY time DESC")
    suspend fun getReplies(newsId: String): List<RealmNews>

    @Query("SELECT * FROM news WHERE replyTo = :newsId")
    suspend fun getDirectReplies(newsId: String): List<RealmNews>

    @Query("SELECT COUNT(*) FROM news WHERE replyTo = :newsId COLLATE NOCASE")
    suspend fun getReplyCount(newsId: String): Int

    @Query(
        "SELECT * FROM news WHERE docType = 'message' COLLATE NOCASE " +
            "AND createdOn = :planetCode COLLATE NOCASE"
    )
    suspend fun getPlanetMessages(planetCode: String): List<RealmNews>

    @Query("SELECT * FROM news WHERE time >= :startTime AND time <= :endTime")
    suspend fun getInTimeRange(startTime: Long, endTime: Long): List<RealmNews>

    @Query("SELECT * FROM news WHERE time >= :startTime AND time <= :endTime AND userId = :userId")
    suspend fun getInTimeRangeForUser(startTime: Long, endTime: Long, userId: String): List<RealmNews>

    @Query("SELECT * FROM news WHERE newsId = :chatId")
    suspend fun getByNewsId(chatId: String): List<RealmNews>

    @Query("SELECT COUNT(*) FROM news WHERE viewableBy = 'teams' AND viewableId = :teamId")
    suspend fun countTeamChats(teamId: String): Long

    @Query("SELECT viewableId FROM news WHERE viewableBy = 'teams' AND viewableId IN (:teamIds)")
    suspend fun getTeamChatViewableIds(teamIds: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(news: RealmNews)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(news: List<RealmNews>)

    @Query("DELETE FROM news WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
