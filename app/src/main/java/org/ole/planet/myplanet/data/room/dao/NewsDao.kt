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

    @Query("SELECT * FROM news WHERE _id IN (:underscoreIds)")
    suspend fun getByUnderscoreIds(underscoreIds: List<String>): List<News>

    @Query("SELECT * FROM news")
    suspend fun getAll(): List<News>

    @Query("SELECT * FROM news WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<News>

    @Query("SELECT * FROM news WHERE replyTo IS NULL OR replyTo = '' ORDER BY time DESC")
    suspend fun getTopLevel(): List<News>

    @Query("SELECT * FROM news WHERE replyTo IS NULL OR replyTo = '' ORDER BY time DESC")
    fun getTopLevelFlow(): Flow<List<News>>

    @Query("SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') AND ((viewableBy = 'teams' COLLATE NOCASE AND viewableId = :teamId COLLATE NOCASE) OR viewIn LIKE :teamPattern ESCAPE '\\') ORDER BY time DESC")
    suspend fun getTopLevelByTeam(teamId: String, teamPattern: String): List<News>

    @Query("SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') AND ((viewableBy = 'teams' COLLATE NOCASE AND viewableId = :teamId COLLATE NOCASE) OR viewIn LIKE :teamPattern ESCAPE '\\') ORDER BY time DESC")
    fun getTopLevelByTeamFlow(teamId: String, teamPattern: String): Flow<List<News>>

    @Query("SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') AND docType = 'message' COLLATE NOCASE ORDER BY time DESC")
    suspend fun getTopLevelMessages(): List<News>

    @Query("SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') AND docType = 'message' COLLATE NOCASE ORDER BY time DESC")
    fun getTopLevelMessagesFlow(): Flow<List<News>>

    @Query("SELECT * FROM news WHERE replyTo = :newsId COLLATE NOCASE ORDER BY time DESC")
    suspend fun getReplies(newsId: String): List<News>

    @Query("SELECT * FROM news WHERE replyTo = :newsId")
    suspend fun getDirectReplies(newsId: String): List<News>

    @Query("SELECT COUNT(*) FROM news WHERE replyTo = :newsId COLLATE NOCASE")
    suspend fun getReplyCount(newsId: String): Int

    @Query("WITH RECURSIVE thread(id) AS (SELECT :newsId UNION SELECT news.id FROM news JOIN thread ON news.replyTo = thread.id) SELECT id FROM thread")
    suspend fun getNewsAndRepliesIds(newsId: String): List<String>

    @Query("SELECT * FROM news WHERE docType = 'message' COLLATE NOCASE AND createdOn = :planetCode COLLATE NOCASE")
    suspend fun getPlanetMessages(planetCode: String): List<News>

    @Query(
        "SELECT COUNT(*) FROM (SELECT DISTINCT strftime('%Y-%m-%d', time / 1000, 'unixepoch', 'localtime') " +
            "FROM news WHERE time >= :startTime AND time <= :endTime " +
            "AND viewIn LIKE '%\"section\":\"community\"%')"
    )
    suspend fun countDistinctCommunityVoiceDates(startTime: Long, endTime: Long): Int

    @Query(
        "SELECT COUNT(*) FROM (SELECT DISTINCT strftime('%Y-%m-%d', time / 1000, 'unixepoch', 'localtime') " +
            "FROM news WHERE time >= :startTime AND time <= :endTime AND userId = :userId " +
            "AND viewIn LIKE '%\"section\":\"community\"%')"
    )
    suspend fun countDistinctCommunityVoiceDatesForUser(startTime: Long, endTime: Long, userId: String): Int

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
