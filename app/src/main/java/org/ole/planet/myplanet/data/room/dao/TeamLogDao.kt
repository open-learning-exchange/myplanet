package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import org.ole.planet.myplanet.model.RealmTeamLog

@Dao
interface TeamLogDao {
    @Query("SELECT * FROM team_log WHERE _rev IS NULL")
    suspend fun getPendingUploads(): List<RealmTeamLog>

    @Query("SELECT * FROM team_log WHERE type = 'teamVisit' AND time > :cutoff AND teamId IN (:teamIds)")
    suspend fun getRecentTeamVisits(cutoff: Long, teamIds: List<String>): List<RealmTeamLog>

    @Query("SELECT * FROM team_log WHERE type = 'teamVisit' AND teamId = :teamId AND user IN (:userNames)")
    suspend fun getTeamVisitsForUsers(teamId: String, userNames: List<String>): List<RealmTeamLog>

    @Query("SELECT MAX(time) FROM team_log WHERE type = 'teamVisit' AND user = :userName AND teamId = :teamId")
    suspend fun getLastVisit(userName: String?, teamId: String?): Long?

    @Query("SELECT * FROM team_log WHERE _id IN (:ids)")
    suspend fun getByRemoteIds(ids: List<String>): List<RealmTeamLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RealmTeamLog)

    @Upsert
    suspend fun upsertAll(logs: List<RealmTeamLog>)

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE team_log SET _id = :remoteId, _rev = :rev WHERE id = :localId")
    suspend fun markUploaded(localId: String, remoteId: String, rev: String): Int
}
