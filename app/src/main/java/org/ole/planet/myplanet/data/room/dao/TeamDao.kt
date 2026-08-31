package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.MyTeam

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams WHERE _id IN (:ids)") suspend fun getByIds(ids: List<String>): List<MyTeam>
    @Query("SELECT resourceId FROM teams WHERE teamId = :teamId AND resourceId IS NOT NULL AND TRIM(resourceId) != '' AND (docType IS NULL OR TRIM(docType) = '' OR docType = 'resourceLink' OR docType = 'link')") suspend fun getResourceIdsByTeamId(teamId: String): List<String>
    @Query("SELECT * FROM teams WHERE _id = :teamId OR teamId = :teamId LIMIT 1") suspend fun getByTeamId(teamId: String): MyTeam?
    @Query("SELECT * FROM teams WHERE _id = :id LIMIT 1") suspend fun getById(id: String): MyTeam?
    @Query("SELECT * FROM teams WHERE userId = :userId") suspend fun getByUserId(userId: String): List<MyTeam>
    @Query("SELECT * FROM teams") suspend fun getAll(): List<MyTeam>
    @Query("SELECT * FROM teams WHERE teamId = :teamId") suspend fun getAllByTeamId(teamId: String): List<MyTeam>
    @Query("SELECT * FROM teams") fun observeAll(): Flow<List<MyTeam>>
    @Query("SELECT * FROM teams WHERE docType = :docType") suspend fun getByDocType(docType: String): List<MyTeam>
    @Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = :docType") suspend fun getByTeamIdAndDocType(teamId: String, docType: String): List<MyTeam>
    @Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = 'membership' AND isLeader = 0 AND (status IS NULL OR status != 'archived') AND (:excludeUserId IS NULL OR userId != :excludeUserId)") suspend fun getEligibleNextLeaderCandidates(teamId: String, excludeUserId: String?): List<MyTeam>
    @Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = :docType") fun observeByTeamIdAndDocType(teamId: String, docType: String): Flow<List<MyTeam>>
    @Query("SELECT * FROM teams WHERE teamId = :teamId AND userId = :userId AND docType = :docType LIMIT 1") suspend fun getByTeamIdUserIdAndDocType(teamId: String, userId: String, docType: String): MyTeam?
    @Query("SELECT COUNT(*) FROM teams WHERE teamId = :teamId AND userId = :userId AND docType = :docType") suspend fun countByTeamIdUserIdAndDocType(teamId: String, userId: String, docType: String): Int
    @Query("SELECT COUNT(DISTINCT userId) FROM teams WHERE teamId = :teamId AND docType = :docType AND isDeletePending = 0 AND userId IS NOT NULL AND EXISTS (SELECT 1 FROM users u WHERE u.id = teams.userId OR u._id = teams.userId)") suspend fun countByTeamIdAndDocType(teamId: String, docType: String): Int
    @Query("DELETE FROM teams WHERE _id = :id") suspend fun deleteById(id: String): Int
    @Query("DELETE FROM teams WHERE _id IN (:ids)") suspend fun deleteByIds(ids: List<String>): Int
    @Query("DELETE FROM teams WHERE teamId = :teamId AND userId = :userId AND docType = :docType") suspend fun deleteByTeamIdUserIdAndDocType(teamId: String, userId: String, docType: String): Int
    @Upsert suspend fun upsertAll(items: List<MyTeam>)
    @Upsert suspend fun upsert(item: MyTeam)

    @Query("SELECT * FROM teams WHERE isUpdated = 1")
    suspend fun getUpdatedTeams(): List<MyTeam>

    @Query("SELECT * FROM teams WHERE (teamId IS NULL OR TRIM(teamId) = '') AND status = 'active'")
    suspend fun getActiveRootTeams(): List<MyTeam>

    @Query("SELECT * FROM teams WHERE (teamId IS NULL OR TRIM(teamId) = '') AND IFNULL(status, '') != 'archived' AND type = :type")
    suspend fun getRootTeamsByType(type: String): List<MyTeam>

    @Query("SELECT * FROM teams WHERE (teamId IS NULL OR TRIM(teamId) = '') AND IFNULL(status, '') != 'archived' AND type = :type AND _id IN (:teamIds)")
    suspend fun getRootTeamsByTypeAndIds(type: String, teamIds: Set<String>): List<MyTeam>

    @Query("SELECT * FROM teams WHERE teamId = :teamId AND resourceId = :resourceId AND docType = 'resourceLink' LIMIT 1")
    suspend fun getResourceLink(teamId: String, resourceId: String): MyTeam?

    @Query("SELECT EXISTS(SELECT 1 FROM teams WHERE name = :name COLLATE NOCASE AND type = :type AND (teamId IS NULL OR TRIM(teamId) = '') AND IFNULL(status, '') != 'archived' AND (_id != :excludeTeamId OR :excludeTeamId IS NULL))")
    suspend fun teamNameExists(name: String, type: String, excludeTeamId: String?): Boolean
}
