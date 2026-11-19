package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.TeamLog

data class TeamVisitCount(
    val teamId: String,
    val visits: Long
)

@Dao
interface TeamLogDao {
    @Query("SELECT * FROM team_log WHERE _rev IS NULL")
    suspend fun getPendingUploads(): List<TeamLog>

    @Query(
        "SELECT teamId, COUNT(*) AS visits FROM team_log " +
            "WHERE type = 'teamVisit' AND time > :cutoff AND teamId IN (:teamIds) GROUP BY teamId"
    )
    suspend fun countRecentTeamVisitsInternal(cutoff: Long, teamIds: List<String>): List<TeamVisitCount>

    suspend fun countRecentTeamVisits(cutoff: Long, teamIds: List<String>): List<TeamVisitCount> {
        if (teamIds.isEmpty()) return emptyList()
        return teamIds.distinct().chunked(900).flatMap { countRecentTeamVisitsInternal(cutoff, it) }
    }

    @Query("SELECT * FROM team_log WHERE type = 'teamVisit' AND teamId = :teamId AND user IN (:userNames)")
    suspend fun getTeamVisitsForUsersInternal(teamId: String, userNames: List<String>): List<TeamLog>

    suspend fun getTeamVisitsForUsers(teamId: String, userNames: List<String>): List<TeamLog> {
        if (userNames.isEmpty()) return emptyList()
        return userNames.distinct().chunked(900).flatMap { getTeamVisitsForUsersInternal(teamId, it) }
    }

    @Query("SELECT MAX(time) FROM team_log WHERE type = 'teamVisit' AND user IS :userName AND teamId IS :teamId")
    suspend fun getLastVisit(userName: String?, teamId: String?): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: TeamLog)

    @Upsert
    suspend fun upsertAll(logs: List<TeamLog>)

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("SELECT * FROM team_log WHERE _id IN (:remoteIds)")
    suspend fun getByRemoteIdsInternal(remoteIds: List<String>): List<TeamLog>

    suspend fun getByRemoteIds(remoteIds: List<String>): List<TeamLog> {
        if (remoteIds.isEmpty()) return emptyList()
        return remoteIds.distinct().chunked(900).flatMap { getByRemoteIdsInternal(it) }
    }

    @Query("DELETE FROM team_log WHERE id IN (:ids)")
    suspend fun deleteByIdsInternal(ids: List<String>)

    suspend fun deleteByIds(ids: List<String>) {
        ids.distinct().chunked(900).forEach { deleteByIdsInternal(it) }
    }

    @Query("UPDATE team_log SET _id = :remoteId, _rev = :rev WHERE id = :localId")
    suspend fun markUploaded(localId: String, remoteId: String, rev: String): Int
}
