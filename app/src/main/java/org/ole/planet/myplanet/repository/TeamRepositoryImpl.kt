package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.service.UserProfileDbHandler

class TeamRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val userProfileDbHandler: UserProfileDbHandler,
) : RealmRepository(databaseService), TeamRepository {

    override suspend fun getTeamResources(teamId: String): List<RealmMyLibrary> {
        val resourceIds = getResourceIds(teamId)
        return if (resourceIds.isEmpty()) {
            emptyList()
        } else {
            queryList(RealmMyLibrary::class.java) {
                `in`("resourceId", resourceIds.toTypedArray())
            }
        }
    }

    override suspend fun isMember(userId: String?, teamId: String): Boolean {
        userId ?: return false
        return queryList(RealmMyTeam::class.java) {
            equalTo("userId", userId)
            equalTo("teamId", teamId)
            equalTo("docType", "membership")
        }.isNotEmpty()
    }

    override suspend fun deleteTask(taskId: String) {
        delete(RealmTeamTask::class.java, "id", taskId)
    }

    override suspend fun upsertTask(task: RealmTeamTask) {
        if (task.link.isNullOrBlank()) {
            val linkObj = JsonObject().apply { addProperty("teams", task.teamId) }
            task.link = Gson().toJson(linkObj)
        }
        if (task.sync.isNullOrBlank()) {
            val syncObj = JsonObject().apply {
                addProperty("type", "local")
                addProperty("planetCode", userProfileDbHandler.userModel?.planetCode)
            }
            task.sync = Gson().toJson(syncObj)
        }
        save(task)
    }

    private suspend fun getResourceIds(teamId: String): List<String> {
        return queryList(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
        }.mapNotNull { it.resourceId?.takeIf { id -> id.isNotBlank() } }
    }
}

