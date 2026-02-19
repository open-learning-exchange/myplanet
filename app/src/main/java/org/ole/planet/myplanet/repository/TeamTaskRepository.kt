package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUser

interface TeamTaskRepository {
    suspend fun getTaskTeamInfo(taskId: String): Triple<String, String, String>?
    suspend fun getTaskNotifications(userId: String?): List<Triple<String, String, String>>
    suspend fun getTasksFlow(userId: String?): Flow<List<RealmTeamTask>>
    suspend fun getTasks(userId: String?): List<RealmTeamTask>
    suspend fun deleteTask(taskId: String)
    suspend fun upsertTask(task: RealmTeamTask)
    suspend fun createTask(title: String, description: String, deadline: Long, teamId: String, assigneeId: String?)
    suspend fun updateTask(taskId: String, title: String, description: String, deadline: Long, assigneeId: String?)
    suspend fun assignTask(taskId: String, assigneeId: String?)
    suspend fun setTaskCompletion(taskId: String, completed: Boolean)
    suspend fun getPendingTasksForUser(userId: String, start: Long, end: Long): List<RealmTeamTask>
    suspend fun markTasksNotified(taskIds: Collection<String>)
    suspend fun getTasksByTeamId(teamId: String): Flow<List<RealmTeamTask>>
    suspend fun getAssignee(userId: String): RealmUser?
}
