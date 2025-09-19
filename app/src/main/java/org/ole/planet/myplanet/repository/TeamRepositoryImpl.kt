package org.ole.planet.myplanet.repository

import android.content.Context
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import java.util.UUID

class TeamRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val uploadManager: UploadManager,
    private val gson: Gson,
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

    override suspend fun getTeamByDocumentIdOrTeamId(id: String): RealmMyTeam? {
        if (id.isBlank()) return null
        return findByField(RealmMyTeam::class.java, "_id", id)
            ?: findByField(RealmMyTeam::class.java, "teamId", id)
    }

    override suspend fun getTeamLinks(): List<RealmMyTeam> {
        return queryList(RealmMyTeam::class.java) {
            equalTo("docType", "link")
        }
    }

    override suspend fun getTeamById(teamId: String): RealmMyTeam? {
        if (teamId.isBlank()) return null
        return findByField(RealmMyTeam::class.java, "_id", teamId)
    }

    override suspend fun isMember(userId: String?, teamId: String): Boolean {
        userId ?: return false
        return queryList(RealmMyTeam::class.java) {
            equalTo("userId", userId)
            equalTo("teamId", teamId)
            equalTo("docType", "membership")
        }.isNotEmpty()
    }

    override suspend fun isTeamLeader(teamId: String, userId: String?): Boolean {
        if (teamId.isBlank() || userId.isNullOrBlank()) return false
        return count(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
            equalTo("docType", "membership")
            equalTo("userId", userId)
            equalTo("isLeader", true)
        } > 0
    }

    override suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean {
        if (teamId.isBlank() || userId.isNullOrBlank()) return false
        return count(RealmMyTeam::class.java) {
            equalTo("docType", "request")
            equalTo("teamId", teamId)
            equalTo("userId", userId)
        } > 0
    }

    override suspend fun requestToJoin(teamId: String, user: RealmUserModel?, teamType: String?) {
        val userId = user?.id ?: return
        val userPlanetCode = user?.planetCode
        if (teamId.isBlank()) return
        executeTransaction { realm ->
            val request = realm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
            request.docType = "request"
            request.createdDate = Date().time
            request.teamType = teamType
            request.userId = userId
            request.teamId = teamId
            request.updated = true
            request.teamPlanetCode = userPlanetCode
            request.userPlanetCode = userPlanetCode
        }
    }

    override suspend fun leaveTeam(teamId: String, userId: String?) {
        if (teamId.isBlank() || userId.isNullOrBlank()) return
        executeTransaction { realm ->
            val memberships = realm.where(RealmMyTeam::class.java)
                .equalTo("userId", userId)
                .equalTo("teamId", teamId)
                .equalTo("docType", "membership")
                .findAll()
            memberships.forEach { member ->
                member?.deleteFromRealm()
            }
        }
    }

    override suspend fun addResourceLinks(
        teamId: String,
        resources: List<RealmMyLibrary>,
        user: RealmUserModel?,
    ) {
        if (teamId.isBlank() || resources.isEmpty() || user == null) return
        executeTransaction { realm ->
            resources.forEach { resource ->
                val teamResource = realm.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
                teamResource.teamId = teamId
                teamResource.title = resource.title
                teamResource.status = user.parentCode
                teamResource.resourceId = resource._id
                teamResource.docType = "resourceLink"
                teamResource.updated = true
                teamResource.teamType = "local"
                teamResource.teamPlanetCode = user.planetCode
                teamResource.userPlanetCode = user.planetCode
            }
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
                addProperty("planetCode", userProfileDbHandler.userModel?.planetCode)
            }
            task.sync = gson.toJson(syncObj)
        }
        save(task)
    }

    override suspend fun assignTask(taskId: String, assigneeId: String?) {
        update(RealmTeamTask::class.java, "id", taskId) { task ->
            task.assignee = assigneeId
            task.isUpdated = true
        }
    }

    override suspend fun updateTeamDetails(
        teamId: String,
        name: String,
        description: String,
        services: String,
        rules: String,
        teamType: String,
        isPublic: Boolean,
        createdBy: String,
    ): Boolean {
        if (teamId.isBlank()) return false
        val updated = AtomicBoolean(false)
        val applyUpdates: (RealmMyTeam) -> Unit = { team ->
            team.name = name
            team.description = description
            team.services = services
            team.rules = rules
            team.teamType = teamType
            team.isPublic = isPublic
            team.createdBy = createdBy.takeIf { it.isNotBlank() } ?: team.createdBy
            team.updated = true
            updated.set(true)
        }

        update(RealmMyTeam::class.java, "_id", teamId, applyUpdates)
        if (!updated.get()) {
            update(RealmMyTeam::class.java, "teamId", teamId, applyUpdates)
        }

        return updated.get()
    }

    override suspend fun syncTeamActivities(context: Context) {
        val applicationContext = context.applicationContext
        val settings = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updateUrl = settings.getString("serverURL", "") ?: ""
        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(updateUrl)

        val primaryAvailable = MainApplication.isServerReachable(mapping.primaryUrl)
        val alternativeAvailable =
            mapping.alternativeUrl?.let { MainApplication.isServerReachable(it) } == true

        if (!primaryAvailable && alternativeAvailable) {
            mapping.alternativeUrl?.let { alternativeUrl ->
                val uri = updateUrl.toUri()
                val editor = settings.edit()
                serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, settings)
            }
        }

        uploadTeamActivities()
    }

    private suspend fun uploadTeamActivities() {
        try {
            withContext(Dispatchers.IO) {
                uploadManager.uploadTeams()
            }
            val apiInterface = client?.create(ApiInterface::class.java)
            withContext(Dispatchers.IO) {
                withRealm { realm ->
                    realm.executeTransaction { transactionRealm ->
                        uploadManager.uploadTeamActivities(transactionRealm, apiInterface)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun getResourceIds(teamId: String): List<String> {
        return queryList(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
        }.mapNotNull { it.resourceId?.takeIf { id -> id.isNotBlank() } }
    }
}

