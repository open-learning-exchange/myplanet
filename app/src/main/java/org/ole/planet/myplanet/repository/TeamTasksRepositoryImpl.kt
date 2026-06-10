package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.TimeUtils.formatDate

class TeamTasksRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val userSessionManager: UserSessionManager,
    private val gson: Gson,
) : RealmRepository(databaseService, realmDispatcher), TeamTasksRepository {

    override fun getTasksFlow(userId: String?): Flow<List<RealmTeamTask>> {
        return queryListFlow(RealmTeamTask::class.java) {
            notEqualTo("status", "archived")
                .equalTo("completed", false)
                .equalTo("assignee", userId)
        }
    }

    override suspend fun getTasks(userId: String?): List<RealmTeamTask> {
        return queryList(RealmTeamTask::class.java) {
            notEqualTo("status", "archived")
                .equalTo("completed", false)
                .equalTo("assignee", userId)
        }
    }

    override suspend fun getTaskTeamInfo(taskId: String): Triple<String, String, String>? {
        val task = findByField(RealmTeamTask::class.java, "id", taskId)
        return task?.let {
            val linkJson = org.json.JSONObject(it.link ?: "{}")
            val teamId = linkJson.optString("teams")
            if (teamId.isNotEmpty()) {
                val teamObject = findByField(RealmMyTeam::class.java, "_id", teamId)
                teamObject?.let { team ->
                    Triple(teamId, team.name ?: "", team.type ?: "")
                }
            } else {
                null
            }
        }
    }

    override suspend fun getTaskNotifications(userId: String?): List<Triple<String, String, String>> {
        if (userId.isNullOrEmpty()) return emptyList()
        return queryList(RealmTeamTask::class.java) {
            notEqualTo("status", "archived")
            equalTo("completed", false)
            equalTo("assignee", userId)
        }.mapNotNull { task ->
            val title = task.title ?: return@mapNotNull null
            val id = task.id ?: return@mapNotNull null
            Triple(title, formatDate(task.deadline), id)
        }
    }

    override suspend fun deleteTask(taskId: String) {
        delete(RealmTeamTask::class.java, "id", taskId)
    }

    override suspend fun upsertTask(task: RealmTeamTask) {
        if (task.link.isNullOrBlank()) {
            val linkObj = JsonObject().apply { addProperty("teams", task.teamId) }
            task.link = gson.toJson(linkObj)
        }
        if (task.sync.isNullOrBlank()) {
            val syncObj = JsonObject().apply {
                addProperty("type", "local")
                addProperty("planetCode", userSessionManager.getUserModel()?.planetCode)
            }
            task.sync = gson.toJson(syncObj)
        }
        save(task)
    }

    override suspend fun createTask(
        title: String,
        description: String,
        deadline: Long,
        teamId: String,
        assigneeId: String?
    ) {
        val realmTeamTask = RealmTeamTask().apply {
            this.id = UUID.randomUUID().toString()
            this.title = title
            this.description = description
            this.deadline = deadline
            this.teamId = teamId
            this.assignee = assigneeId
            this.isUpdated = true
        }
        upsertTask(realmTeamTask)
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        deadline: Long,
        assigneeId: String?
    ) {
        update(RealmTeamTask::class.java, "id", taskId) { task ->
            task.title = title
            task.description = description
            task.deadline = deadline
            task.assignee = assigneeId
            task.isUpdated = true
        }
    }

    override suspend fun assignTask(taskId: String, assigneeId: String?) {
        update(RealmTeamTask::class.java, "id", taskId) { task ->
            task.assignee = assigneeId
            task.isUpdated = true
        }
    }

    override suspend fun setTaskCompletion(taskId: String, completed: Boolean) {
        update(RealmTeamTask::class.java, "id", taskId) { task ->
            task.completed = completed
            task.completedTime = if (completed) Date().time else 0
            task.isUpdated = true
        }
    }

    override suspend fun getPendingTasksForUser(userId: String, start: Long, end: Long): List<RealmTeamTask> {
        if (userId.isBlank() || start > end) return emptyList()
        return queryList(RealmTeamTask::class.java) {
            equalTo("completed", false)
            equalTo("assignee", userId)
            equalTo("isNotified", false)
            between("deadline", start, end)
        }
    }

    override suspend fun markTasksNotified(taskIds: Collection<String>) {
        if (taskIds.isEmpty()) return
        val validIds = taskIds.mapNotNull { it.takeIf(String::isNotBlank) }.distinct()
        if (validIds.isEmpty()) return
        executeTransaction { realm ->
            val tasks = realm.where(RealmTeamTask::class.java)
                .`in`("id", validIds.toTypedArray())
                .findAll()
            tasks.forEach { task ->
                task.isNotified = true
            }
        }
    }

    override suspend fun getTasksByTeamId(teamId: String): Flow<List<RealmTeamTask>> {
        return queryListFlow(RealmTeamTask::class.java) {
            equalTo("teamId", teamId)
            notEqualTo("status", "archived")
        }
    }
}
