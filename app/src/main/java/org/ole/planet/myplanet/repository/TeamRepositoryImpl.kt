package org.ole.planet.myplanet.repository

import android.content.Context
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.RealmChangeListener
import io.realm.RealmResults
import io.realm.Sort
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.ServerUrlMapper

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

    override fun getTeamTransactions(
        teamId: String,
        startDate: Long?,
        endDate: Long?,
        sortAscending: Boolean,
    ): Flow<RealmResults<RealmMyTeam>> {
        val sortOrder = if (sortAscending) Sort.ASCENDING else Sort.DESCENDING
        return withRealmFlow { realm, scope ->
            val query = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("docType", "transaction")
                .notEqualTo("status", "archived")

            startDate?.let { query.greaterThanOrEqualTo("date", it) }
            endDate?.let { query.lessThanOrEqualTo("date", it) }

            val results = query.findAllAsync().sort("date", sortOrder)
            val listener = RealmChangeListener<RealmResults<RealmMyTeam>> { updatedResults ->
                scope.trySend(updatedResults)
            }
            results.addChangeListener(listener)
            scope.trySend(results)

            return@withRealmFlow { results.removeChangeListener(listener) }
        }
    }

    override suspend fun createTransaction(
        teamId: String,
        type: String,
        note: String,
        amount: Int,
        date: Long,
        parentCode: String?,
        planetCode: String?,
    ): Result<Unit> {
        if (teamId.isBlank()) {
            return Result.failure(IllegalArgumentException("teamId cannot be blank"))
        }
        return runCatching {
            executeTransaction { realm ->
                val transaction = realm.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
                transaction.status = "active"
                transaction.date = date
                transaction.type = type
                transaction.description = note
                transaction.teamId = teamId
                transaction.amount = amount
                transaction.parentCode = parentCode
                transaction.teamPlanetCode = planetCode
                transaction.teamType = "sync"
                transaction.docType = "transaction"
                transaction.updated = true
            }
        }
    }

    override suspend fun isMember(userId: String?, teamId: String): Boolean {
        userId ?: return false
        return count(RealmMyTeam::class.java) {
            equalTo("userId", userId)
            equalTo("teamId", teamId)
            equalTo("docType", "membership")
        } > 0
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

    override suspend fun getRecentVisitCounts(teamIds: Collection<String>): Map<String, Long> {
        if (teamIds.isEmpty()) return emptyMap()

        val validIds = teamIds.filter { it.isNotBlank() }.distinct()
        if (validIds.isEmpty()) return emptyMap()

        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis

        val recentLogs = queryList(RealmTeamLog::class.java) {
            equalTo("type", "teamVisit")
            greaterThan("time", cutoff)
            `in`("teamId", validIds.toTypedArray())
        }

        return recentLogs
            .mapNotNull { it.teamId }
            .groupingBy { it }
            .eachCount()
            .mapValues { it.value.toLong() }
    }

    override suspend fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?) {
        if (teamId.isBlank() || userId.isNullOrBlank()) return
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

    override suspend fun removeResourceLink(teamId: String, resourceId: String) {
        if (teamId.isBlank() || resourceId.isBlank()) return
        executeTransaction { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("resourceId", resourceId)
                .equalTo("docType", "resourceLink")
                .findFirst()
                ?.let { teamResource ->
                    teamResource.resourceId = ""
                    teamResource.updated = true
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

    override suspend fun setTaskCompletion(taskId: String, completed: Boolean) {
        update(RealmTeamTask::class.java, "id", taskId) { task ->
            task.completed = completed
            task.completedTime = if (completed) Date().time else 0
            task.isUpdated = true
        }
    }

    override suspend fun getUserDisplayNames(userIds: Collection<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()

        val validIds = userIds.filter { it.isNotBlank() }.distinct()
        if (validIds.isEmpty()) return emptyMap()

        return withRealmAsync { realm ->
            realm.where(RealmUserModel::class.java)
                .`in`("id", validIds.toTypedArray())
                .findAll()
                .mapNotNull { user ->
                    val id = user.id ?: return@mapNotNull null
                    val fallbackName = listOfNotNull(user.firstName, user.middleName, user.lastName)
                        .joinToString(" ")
                        .trim()
                    val displayName = when {
                        !user.name.isNullOrBlank() -> user.name!!
                        fallbackName.isNotBlank() -> fallbackName
                        else -> ""
                    }
                    id to displayName
                }
                .toMap()
        }
    }

    override suspend fun createTeam(
        category: String?,
        name: String,
        description: String,
        services: String,
        rules: String,
        teamType: String?,
        isPublic: Boolean,
        user: RealmUserModel,
    ): Result<String> {
        return runCatching {
            val teamId = AndroidDecrypter.generateIv()
            executeTransaction { realm ->
                val team = realm.createObject(RealmMyTeam::class.java, teamId)
                team.status = "active"
                team.createdDate = Date().time
                if (category == "enterprise") {
                    team.type = "enterprise"
                    team.services = services
                    team.rules = rules
                } else {
                    team.type = "team"
                    team.teamType = teamType
                }
                team.name = name
                team.description = description
                team.createdBy = user._id
                team.teamId = ""
                team.isPublic = isPublic
                team.userId = user.id
                team.parentCode = user.parentCode
                team.teamPlanetCode = user.planetCode
                team.updated = true

                val membershipId = AndroidDecrypter.generateIv()
                val membership = realm.createObject(RealmMyTeam::class.java, membershipId)
                membership.userId = user._id
                membership.teamId = teamId
                membership.teamPlanetCode = user.planetCode
                membership.userPlanetCode = user.planetCode
                membership.docType = "membership"
                membership.isLeader = true
                membership.teamType = teamType
                membership.updated = true
            }
            teamId
        }
    }

    override suspend fun updateTeam(
        teamId: String,
        name: String,
        description: String,
        services: String,
        rules: String,
        updatedBy: String?,
    ): Result<Boolean> {
        return runCatching {
            var updated = false
            executeTransaction { realm ->
                val teamToUpdate = realm.where(RealmMyTeam::class.java)
                    .equalTo("_id", teamId)
                    .findFirst()
                    ?: realm.where(RealmMyTeam::class.java)
                        .equalTo("teamId", teamId)
                        .findFirst()
                teamToUpdate?.let { team ->
                    team.name = name
                    team.services = services
                    team.rules = rules
                    team.description = description
                    updatedBy?.let { team.createdBy = it }
                    team.limit = 12
                    team.updated = true
                    updated = true
                }
            }
            updated
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
            val apiInterface = client?.create(ApiInterface::class.java)
            withContext(Dispatchers.IO) {
                uploadManager.uploadTeams()
                executeTransaction { realm ->
                    uploadManager.uploadTeamActivities(realm, apiInterface)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun getResourceIds(teamId: String): List<String> {
        return queryList(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
            beginGroup()
                .isNull("docType")
                .or().equalTo("docType", "")
                .or().equalTo("docType", "resourceLink")
                .or().equalTo("docType", "link")
            endGroup()
            isNotNull("resourceId")
            isNotEmpty("resourceId")
        }.mapNotNull { it.resourceId }
    }
}

