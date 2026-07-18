package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmTeamTask

@Dao
interface TeamTaskDao {
    @Query("SELECT * FROM team_tasks WHERE status != 'archived' AND completed = 0 AND assignee = :userId")
    fun getOpenTasksForUser(userId: String?): Flow<List<RealmTeamTask>>

    @Query("SELECT * FROM team_tasks WHERE completed = 0 AND assignee = :userId AND isNotified = 0 AND deadline BETWEEN :start AND :end")
    suspend fun getPendingTasksForUser(userId: String, start: Long, end: Long): List<RealmTeamTask>

    @Query("UPDATE team_tasks SET isNotified = 1 WHERE id IN (:taskIds)")
    suspend fun markTasksNotified(taskIds: List<String>)

    @Query("SELECT * FROM team_tasks WHERE teamId = :teamId AND status != 'archived'")
    fun getTasksByTeamId(teamId: String): Flow<List<RealmTeamTask>>

    @Query("SELECT * FROM team_tasks WHERE (_id IS NULL OR _id = '' OR isUpdated = 1)")
    suspend fun getPendingUploads(): List<RealmTeamTask>

    @Query("DELETE FROM team_tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: String)

    @Upsert
    suspend fun upsert(task: RealmTeamTask)

    @Upsert
    suspend fun upsertAll(tasks: List<RealmTeamTask>)

    @Query("SELECT * FROM team_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: String): RealmTeamTask?

    @Query("SELECT * FROM team_tasks WHERE id IN (:taskIds)")
    suspend fun getByIds(taskIds: List<String>): List<RealmTeamTask>

    @Query("SELECT * FROM team_tasks WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): RealmTeamTask?

    @Query("SELECT * FROM team_tasks WHERE title IN (:titles)")
    suspend fun getByTitles(titles: List<String>): List<RealmTeamTask>

    @Query("SELECT * FROM team_tasks WHERE assignee = :userId AND deadline BETWEEN :start AND :end")
    suspend fun getTasksForUserBetween(userId: String, start: Long, end: Long): List<RealmTeamTask>

    @Query("UPDATE team_tasks SET _id = :remoteId, _rev = :remoteRev, isUpdated = 0 WHERE id = :localId")
    suspend fun markUploaded(localId: String, remoteId: String?, remoteRev: String?): Int
}
