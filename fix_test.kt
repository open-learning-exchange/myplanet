package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.News

@Dao
interface NewsDao {
// ...
    @Query("SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') AND ((viewableBy = 'teams' COLLATE NOCASE AND viewableId = :teamId COLLATE NOCASE) OR viewIn LIKE :teamPattern ESCAPE '\\') ORDER BY time DESC")
    suspend fun getTopLevelByTeam(teamId: String, teamPattern: String): List<News>

    @Query("SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') AND ((viewableBy = 'teams' COLLATE NOCASE AND viewableId = :teamId COLLATE NOCASE) OR viewIn LIKE :teamPattern ESCAPE '\\') ORDER BY time DESC")
    fun getTopLevelByTeamFlow(teamId: String, teamPattern: String): Flow<List<News>>
// ...
