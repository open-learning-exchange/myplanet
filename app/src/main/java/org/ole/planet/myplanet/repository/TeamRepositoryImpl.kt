package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import android.util.Log
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper

class TeamRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val uploadManager: UploadManager,
    private val gson: Gson,
    @AppPreferences private val preferences: SharedPreferences,
    private val serverUrlMapper: ServerUrlMapper,
) : RealmRepository(databaseService), TeamRepository {

    override suspend fun getShareableTeams(): List<RealmMyTeam> {
        return queryList(RealmMyTeam::class.java) {
            isEmpty("teamId")
            notEqualTo("status", "archived")
            equalTo("type", "team")
        }
    }

    override suspend fun getShareableEnterprises(): List<RealmMyTeam> {
        return queryList(RealmMyTeam::class.java) {
            isEmpty("teamId")
            notEqualTo("status", "archived")
            equalTo("type", "enterprise")
        }
    }

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
    ): Flow<List<RealmMyTeam>> {
        return queryListFlow(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
            equalTo("docType", "transaction")
            notEqualTo("status", "archived")
            startDate?.let { greaterThanOrEqualTo("date", it) }
            endDate?.let { lessThanOrEqualTo("date", it) }
        }.map { transactions ->
            if (sortAscending) {
                transactions.sortedBy { it.date }
            } else {
                transactions.sortedByDescending { it.date }
            }
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

    override suspend fun addReport(report: JsonObject) {
        executeTransaction { realm ->
            val reportId = JsonUtils.getString("_id", report)
            val reportEntry = realm.where(RealmMyTeam::class.java)
                .equalTo("_id", reportId)
                .findFirst()
                ?: realm.createObject(RealmMyTeam::class.java, reportId)
            RealmMyTeam.populateTeamFields(report, reportEntry)
        }
    }

    override suspend fun updateReport(reportId: String, payload: JsonObject) {
        if (reportId.isBlank()) return
        executeTransaction { realm ->
            val report = realm.where(RealmMyTeam::class.java)
                .equalTo("_id", reportId)
                .findFirst()
                ?: return@executeTransaction
            RealmMyTeam.populateReportFields(payload, report)
            report.updated = true
            if (report.updatedDate == 0L) {
                report.updatedDate = System.currentTimeMillis()
            }
        }
    }

    override suspend fun archiveReport(reportId: String) {
        if (reportId.isBlank()) return
        executeTransaction { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("_id", reportId)
                .findFirst()?.apply {
                    status = "archived"
                    updated = true
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

    override suspend fun respondToMemberRequest(
        teamId: String,
        userId: String,
        accept: Boolean,
    ): Result<Unit> {
        if (teamId.isBlank() || userId.isBlank()) {
            return Result.failure(IllegalArgumentException("teamId and userId cannot be blank"))
        }

        return runCatching {
            executeTransaction { realm ->
                val request = realm.where(RealmMyTeam::class.java)
                    .equalTo("teamId", teamId)
                    .equalTo("userId", userId)
                    .equalTo("docType", "request")
                    .findFirst()
                    ?: throw IllegalStateException("Request not found for user $userId")

                if (accept) {
                    request.docType = "membership"
                    request.updated = true
                } else {
                    request.deleteFromRealm()
                }
            }
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

    override suspend fun removeMember(teamId: String, userId: String) {
        if (teamId.isBlank() || userId.isBlank()) return
        executeTransaction { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userId)
                .equalTo("docType", "membership")
                .findAll()
                .deleteAllFromRealm()
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

    override suspend fun getPendingTasksForUser(
        userId: String,
        start: Long,
        end: Long,
    ): List<RealmTeamTask> {
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

    override suspend fun logTeamVisit(
        teamId: String,
        userName: String?,
        userPlanetCode: String?,
        userParentCode: String?,
        teamType: String?,
    ) {
        if (teamId.isBlank() || userName.isNullOrBlank()) return
        executeTransaction { realm ->
            val log = realm.createObject(RealmTeamLog::class.java, UUID.randomUUID().toString())
            log.teamId = teamId
            log.user = userName
            log.createdOn = userPlanetCode
            log.type = "teamVisit"
            log.teamType = teamType
            log.parentCode = userParentCode
            log.time = Date().time
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
        val startTime = System.currentTimeMillis()
        Log.d("TeamRepositoryImpl", "[${startTime}ms] createTeam() called - category: $category, name: $name, teamType: $teamType, isPublic: $isPublic, userId: ${user._id}")
        return runCatching {
            val idGenTime = System.currentTimeMillis()
            Log.d("TeamRepositoryImpl", "[${idGenTime}ms] (+${idGenTime - startTime}ms) Generating team ID...")
            val teamId = AndroidDecrypter.generateIv()
            val afterIdTime = System.currentTimeMillis()
            Log.d("TeamRepositoryImpl", "[${afterIdTime}ms] (+${afterIdTime - idGenTime}ms) Generated team ID: $teamId")

            val preTransactionTime = System.currentTimeMillis()
            Log.d("TeamRepositoryImpl", "[${preTransactionTime}ms] (+${preTransactionTime - afterIdTime}ms) Starting Realm transaction to create team...")
            executeTransaction { realm ->
                val insideTransTime = System.currentTimeMillis()
                Log.d("TeamRepositoryImpl", "[${insideTransTime}ms] (+${insideTransTime - preTransactionTime}ms) Inside Realm transaction - creating team object")
                val team = realm.createObject(RealmMyTeam::class.java, teamId)
                val afterCreateTime = System.currentTimeMillis()
                Log.d("TeamRepositoryImpl", "[${afterCreateTime}ms] (+${afterCreateTime - insideTransTime}ms) Team object created, setting properties...")
                team.status = "active"
                team.createdDate = Date().time
                if (category == "enterprise") {
                    Log.d("TeamRepositoryImpl", "Setting team as enterprise type")
                    team.type = "enterprise"
                    team.services = services
                    team.rules = rules
                } else {
                    Log.d("TeamRepositoryImpl", "Setting team as regular team type")
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
                val afterPropsTime = System.currentTimeMillis()
                Log.d("TeamRepositoryImpl", "[${afterPropsTime}ms] (+${afterPropsTime - afterCreateTime}ms) Team object populated")

                val preMembershipTime = System.currentTimeMillis()
                Log.d("TeamRepositoryImpl", "[${preMembershipTime}ms] (+${preMembershipTime - afterPropsTime}ms) Creating membership for creator...")
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
                val afterMembershipTime = System.currentTimeMillis()
                Log.d("TeamRepositoryImpl", "[${afterMembershipTime}ms] (+${afterMembershipTime - preMembershipTime}ms) Membership created with ID: $membershipId")
            }
            val postTransactionTime = System.currentTimeMillis()
            Log.d("TeamRepositoryImpl", "[${postTransactionTime}ms] (+${postTransactionTime - preTransactionTime}ms) Realm transaction completed successfully")
            teamId
        }.onSuccess {
            val successTime = System.currentTimeMillis()
            Log.d("TeamRepositoryImpl", "[${successTime}ms] (+${successTime - startTime}ms total) createTeam() succeeded - returning team ID: $it")
        }.onFailure { exception ->
            Log.e("TeamRepositoryImpl", "createTeam() failed with exception", exception)
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
        Log.d("TeamRepositoryImpl", "updateTeam() called - teamId: $teamId, name: $name, updatedBy: $updatedBy")
        return runCatching {
            var updated = false
            Log.d("TeamRepositoryImpl", "Starting Realm transaction to update team...")
            executeTransaction { realm ->
                Log.d("TeamRepositoryImpl", "Searching for team by _id: $teamId")
                val teamToUpdate = realm.where(RealmMyTeam::class.java)
                    .equalTo("_id", teamId)
                    .findFirst()
                    ?: run {
                        Log.d("TeamRepositoryImpl", "Team not found by _id, searching by teamId: $teamId")
                        realm.where(RealmMyTeam::class.java)
                            .equalTo("teamId", teamId)
                            .findFirst()
                    }
                if (teamToUpdate != null) {
                    Log.d("TeamRepositoryImpl", "Team found, updating fields...")
                    teamToUpdate.name = name
                    teamToUpdate.services = services
                    teamToUpdate.rules = rules
                    teamToUpdate.description = description
                    updatedBy?.let { teamToUpdate.createdBy = it }
                    teamToUpdate.limit = 12
                    teamToUpdate.updated = true
                    updated = true
                    Log.d("TeamRepositoryImpl", "Team updated successfully")
                } else {
                    Log.w("TeamRepositoryImpl", "Team not found with id: $teamId")
                }
            }
            Log.d("TeamRepositoryImpl", "Realm transaction completed - updated: $updated")
            updated
        }.onSuccess {
            Log.d("TeamRepositoryImpl", "updateTeam() succeeded - result: $it")
        }.onFailure { exception ->
            Log.e("TeamRepositoryImpl", "updateTeam() failed with exception", exception)
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

    override suspend fun syncTeamActivities() {
        val updateUrl = preferences.getString("serverURL", "") ?: ""
        val mapping = serverUrlMapper.processUrl(updateUrl)

        val primaryAvailable = MainApplication.isServerReachable(mapping.primaryUrl)
        val alternativeAvailable =
            mapping.alternativeUrl?.let { MainApplication.isServerReachable(it) } == true

        if (!primaryAvailable && alternativeAvailable) {
            mapping.alternativeUrl?.let { alternativeUrl ->
                val uri = updateUrl.toUri()
                val editor = preferences.edit()
                serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, preferences)
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

