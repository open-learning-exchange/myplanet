package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.text.TextUtils
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.TeamLogDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.legacy.TeamDao
import org.ole.planet.myplanet.data.room.dao.legacy.UserDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomTeamEntity
import org.ole.planet.myplanet.data.room.entity.legacy.toRealmModel
import org.ole.planet.myplanet.data.room.entity.legacy.toRoomEntity
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.CreateTeamRequest
import org.ole.planet.myplanet.model.FinanceReportParams
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamLog
import org.ole.planet.myplanet.model.TeamResourceDto
import org.ole.planet.myplanet.model.TeamStatus
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils

class TeamsRepositoryImpl @Inject constructor(
    private val activitiesRepository: ActivitiesRepository,
    private val userSessionManager: UserSessionManager,
    private val uploadManager: UploadManager,
    private val gson: Gson,
    @param:AppPreferences private val preferences: SharedPreferences,
    private val sharedPrefManager: SharedPrefManager,
    private val serverUrlMapper: ServerUrlMapper,
    private val dispatcherProvider: DispatcherProvider,
    private val userRepository: UserRepository,
    private val resourcesRepositoryLazy: dagger.Lazy<ResourcesRepository>,
    private val timeProvider: TimeProvider,
    private val teamLogDao: TeamLogDao,
    private val teamTaskDao: TeamTaskDao,
    private val myLibraryDao: MyLibraryDao,
    private val teamDao: TeamDao,
    private val userDao: UserDao,
    private val courseDao: CourseDao,
    private val courseStepDao: CourseStepDao,
) : TeamsRepository, TeamsSyncRepository {
    override fun getTasksFlow(userId: String?): Flow<List<TeamTask>> {
        return teamTaskDao.getOpenTasksForUser(userId)
    }

    override suspend fun getTeamsForUpload(): List<TeamUploadData> {
        val teams = teamDao.getAll().filter { it.isUpdated }.map { it.toRealmModel() }
        val courseIds = teams.flatMap { it.courses.orEmpty() }.filter { it.isNotBlank() }.distinct()
        val courses = getCoursesForSerialization(courseIds)
        val coursesResourcesMap = if (courseIds.isNotEmpty()) {
            myLibraryDao.getByCourseIds(courseIds)
                .groupBy { it.courseId ?: "" }
                .mapValues { (_, items) -> items.groupBy { it.stepId } }
        } else {
            emptyMap()
        }

        return teams.map { team ->
            TeamUploadData(
                teamId = team._id,
                serialized = MyTeam.serialize(team, courses, coursesResourcesMap),
                isDeletePending = team.isDeletePending,
                imageName = team.imageName
            )
        }
    }

    override suspend fun deleteLocalTeamRecord(teamId: String?) {
        if (teamId.isNullOrBlank()) return
        teamDao.deleteById(teamId)
    }

    override suspend fun deleteLocalTeamRecords(teamIds: List<String>) {
        if (teamIds.isEmpty()) return
        teamIds.filter { it.isNotBlank() }.distinct().forEach { teamDao.deleteById(it) }
    }

    override suspend fun markTeamUploaded(teamId: String?, rev: String) {
        if (teamId.isNullOrBlank()) return
        updateTeamEntityById(teamId) {
            it._rev = rev
            it.updated = false
        }
    }

    override suspend fun markTeamsUploaded(uploadedTeams: Map<String, String>) {
        if (uploadedTeams.isEmpty()) return
        val teamsToUpdate = teamDao.getAll()
            .filter { (it._id ?: it.id) in uploadedTeams.keys }
            .map { entity ->
                entity.toRealmModel().apply {
                    _rev = uploadedTeams[_id ?: entity.id]
                    updated = false
                }.requireRoomEntity()
            }
        if (teamsToUpdate.isNotEmpty()) {
            teamDao.upsertAll(teamsToUpdate)
        }
    }

    override suspend fun createTeamAndAddMember(request: CreateTeamRequest, user: UserEntity): Result<String> {
        return runCatching {
            val teamId = AndroidDecrypter.generateIv()
            val team = MyTeam().apply {
                _id = teamId
                status = "active"
                createdDate = Date().time
                if (request.category == "enterprise") {
                    type = "enterprise"
                    services = request.services
                    rules = request.rules
                } else {
                    type = "team"
                    teamType = request.teamType
                }
                name = request.name
                description = request.description
                createdBy = user._id
                this.teamId = ""
                isPublic = request.isPublic
                userId = user._id
                parentCode = user.parentCode
                teamPlanetCode = user.planetCode
                updated = true
            }
            val membership = MyTeam().apply {
                _id = AndroidDecrypter.generateIv()
                userId = user._id
                this.teamId = teamId
                teamPlanetCode = user.planetCode
                userPlanetCode = user.planetCode
                docType = "membership"
                isLeader = true
                teamType = request.teamType
                updated = true
            }
            teamDao.upsertAll(listOf(team.requireRoomEntity(), membership.requireRoomEntity()))
            teamId
        }
    }

    override suspend fun getAllActiveTeams(): List<MyTeam> {
        return teamDao.getAll()
            .filter { it.isRootTeam() && it.status == "active" }
            .map { it.toRealmModel() }
    }

    override suspend fun getMyTeamsFlow(userId: String): Flow<List<MyTeam>> {
        return teamDao.observeAll().map { entities ->
            val teamIds = entities.filter {
                it.userId == userId &&
                    it.docType == "membership" &&
                    !it.isDeletePending &&
                    !it.teamId.isNullOrBlank()
            }.mapNotNull { it.teamId }.toSet()

            if (teamIds.isEmpty()) {
                emptyList()
            } else {
                entities.filter {
                    (it._id ?: it.id) in teamIds &&
                        it.status != "archived" &&
                        it.isRootTeam()
                }.map { it.toRealmModel() }
            }
        }
    }

    private suspend fun getMemberTeamIds(userId: String): Set<String> {
        return teamDao.getByUserId(userId)
            .filter { it.docType == "membership" }
            .mapNotNull { it.teamId }
            .toHashSet()
    }

    private suspend fun getShareableTeams(userId: String?): List<MyTeam> {
        val allTeams = teamDao.getAll()
        return if (userId.isNullOrBlank()) {
            allTeams.filter {
                it.isRootTeam() &&
                    it.status != "archived" &&
                    it.type == "team"
            }.map { it.toRealmModel() }
        } else {
            val memberIds = getMemberTeamIds(userId)
            if (memberIds.isEmpty()) {
                emptyList()
            } else {
                allTeams.filter {
                    it.isRootTeam() &&
                        it.status != "archived" &&
                        it.type == "team" &&
                        (it._id ?: it.id) in memberIds
                }.map { it.toRealmModel() }
            }
        }
    }

    override suspend fun getTeamSummaries(userId: String?): List<TeamSummary> {
        return getShareableTeams(userId).mapNotNull { it.toSummary() }
    }

    private suspend fun getShareableEnterprises(): List<MyTeam> {
        return teamDao.getAll()
            .filter {
                it.isRootTeam() &&
                    it.status != "archived" &&
                    it.type == "enterprise"
            }
            .map { it.toRealmModel() }
    }

    private suspend fun mapToTeamDetails(teams: List<MyTeam>, userId: String?): List<TeamDetails> {
        val validTeams = teams.filter { !it._id.isNullOrBlank() && it.status != "archived" }
        if (validTeams.isEmpty()) return emptyList()

        val teamIds = validTeams.mapNotNull { it._id }
        val visitCounts = getRecentVisitCounts(teamIds)
        val memberStatuses = getTeamMemberStatuses(userId, teamIds)

        return validTeams.map { team ->
            val teamId = team._id ?: ""
            val status = memberStatuses[teamId]
            TeamDetails(
                _id = team._id,
                name = team.name ?: "",
                teamType = team.teamType,
                createdDate = team.createdDate,
                type = team.type,
                status = team.status,
                visitCount = visitCounts[teamId] ?: 0L,
                teamStatus = status?.let {
                    TeamStatus(
                        isMember = it.isMember,
                        isLeader = it.isLeader,
                        hasPendingRequest = it.hasPendingRequest
                    )
                },
                description = team.description,
                services = team.services,
                rules = team.rules,
                teamId = team.teamId
            )
        }.sortedWith(
            compareByDescending<TeamDetails> {
                when {
                    it.teamStatus?.isLeader == true -> 3
                    it.teamStatus?.isMember == true -> 2
                    else -> 1
                }
            }.thenByDescending { it.visitCount }
        )
    }

    override fun getMyTeamDetailsFlow(userId: String): Flow<List<TeamDetails>> {
        return teamDao.observeAll().map { entities ->
            val teamIds = entities.filter {
                it.userId == userId && it.docType == "membership"
            }.mapNotNull { it.teamId }.toSet()

            val teams = if (teamIds.isEmpty()) {
                emptyList()
            } else {
                entities.filter {
                    it.isRootTeam() &&
                        (it._id ?: it.id) in teamIds &&
                        it.status != "archived"
                }.map { it.toRealmModel() }
            }
            mapToTeamDetails(teams, userId)
        }
    }

    override suspend fun getShareableEnterpriseDetails(userId: String?): List<TeamDetails> {
        val all = teamDao.getAll()
            .filter {
                it.isRootTeam() &&
                    it.status != "archived" &&
                    it.type == "enterprise"
            }
            .map { it.toRealmModel() }
        return mapToTeamDetails(all, userId)
    }

    override suspend fun getTeamDetails(userId: String?): List<TeamDetails> {
        val all = teamDao.getAll()
            .filter {
                it.isRootTeam() &&
                    it.status != "archived" &&
                    it.type == "team"
            }
            .map { it.toRealmModel() }
        return mapToTeamDetails(all, userId)
    }

    override suspend fun getShareableEnterpriseSummaries(userId: String?): List<TeamSummary> {
        val filtered = if (userId.isNullOrBlank()) {
            getShareableEnterprises()
        } else {
            val memberIds = getMemberTeamIds(userId)
            if (memberIds.isEmpty()) {
                emptyList()
            } else {
                teamDao.getAll().filter {
                    it.isRootTeam() &&
                        it.status != "archived" &&
                        it.type == "enterprise" &&
                        (it._id ?: it.id) in memberIds
                }.map { it.toRealmModel() }
            }
        }
        return filtered.mapNotNull { it.toSummary() }
    }

    override suspend fun getTeamResources(teamId: String): List<MyLibrary> {
        val resourceIds = getResourceIds(teamId)
        val linkedResources = resourcesRepositoryLazy.get().getLibraryItemsByResourceIds(resourceIds)
        val privateResources = resourcesRepositoryLazy.get().getTeamPrivateResources(teamId)
        return (linkedResources + privateResources).distinctBy { it.id }
    }

    override suspend fun getTeamCourseIds(teamId: String): List<String> {
        return getTeamEntityByAnyId(teamId)?.courses.orEmpty()
    }

    override suspend fun addCoursesToTeam(teamId: String, courseIds: List<String>): Result<Unit> {
        return runCatching {
            if (courseIds.isEmpty()) return@runCatching
            updateTeamEntityById(teamId) { team ->
                val merged = (team.courses.orEmpty() + courseIds).filter { it.isNotBlank() }.distinct()
                team.courses = merged.toMutableList()
                team.updated = true
            }
        }
    }

    override suspend fun removeCourseFromTeam(teamId: String, courseId: String): Result<Unit> {
        return runCatching {
            updateTeamEntityById(teamId) { team ->
                team.courses = team.courses.orEmpty().filterNot { it == courseId }.toMutableList()
                team.updated = true
            }
        }
    }

    override suspend fun getTeamByIdOrTeamId(id: String): MyTeam? {
        if (id.isBlank()) return null
        return getTeamEntityByAnyId(id)?.toRealmModel()
    }

    override suspend fun getTeamLinks(): List<MyTeam> {
        return teamDao.getByDocType("link").map { it.toRealmModel() }
    }

    override suspend fun getTeamById(teamId: String): MyTeam? {
        if (teamId.isBlank()) return null
        return teamDao.getById(teamId)?.toRealmModel()
    }

    override suspend fun getTeamSummaryById(teamId: String): TeamSummary? {
        return getTeamById(teamId)?.toSummary()
    }

    override suspend fun getTaskTeamInfo(taskId: String): Triple<String, String, String>? {
        val task = teamTaskDao.getById(taskId) ?: return null
        val linkJson = org.json.JSONObject(task.link ?: "{}")
        val teamId = linkJson.optString("teams")
        if (teamId.isEmpty()) return null
        val teamObject = teamDao.getById(teamId)?.toRealmModel() ?: return null
        return Triple(teamId, teamObject.name ?: "", teamObject.type ?: "")
    }

    override suspend fun getTeamLabelInfo(teamId: String): TeamLabelInfo? {
        val team = teamDao.getById(teamId)?.toRealmModel() ?: return null
        return TeamLabelInfo(
            teamId = team._id ?: "",
            name = team.name ?: "",
            type = team.type ?: ""
        )
    }

    override suspend fun getJoinRequestInfo(requestId: String?): JoinRequestInfo? {
        if (requestId.isNullOrEmpty()) return null
        val req = teamDao.getById(requestId)?.toRealmModel() ?: return null
        return JoinRequestInfo(
            id = req._id ?: "",
            teamId = req.teamId ?: "",
            userId = req.userId ?: ""
        )
    }

    override suspend fun getJoinRequestsInfo(requestIds: List<String>): List<JoinRequestInfo> {
        if (requestIds.isEmpty()) return emptyList()
        return teamDao.getAll()
            .filter { (it._id ?: it.id) in requestIds }
            .map { entity ->
                JoinRequestInfo(
                    id = entity._id ?: entity.id,
                    teamId = entity.teamId ?: "",
                    userId = entity.userId ?: ""
                )
            }
    }

    override suspend fun getTeamNamesByIds(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        return teamDao.getAll()
            .filter { (it._id ?: it.id) in ids }
            .associateBy({ it._id ?: it.id }, { it.name ?: "Unknown Team" })
    }

    override suspend fun getJoinRequestTeamId(requestId: String): String? {
        val request = teamDao.getById(requestId) ?: return null
        return if (request.docType == "request") request.teamId else null
    }

    override suspend fun getTeamTransactionsWithBalance(
        teamId: String,
        startDate: Long?,
        endDate: Long?,
        sortAscending: Boolean,
    ): Flow<List<Transaction>> {
        return queryTransactions(teamId, startDate, endDate, true).map { transactions ->
            val transactionDataList = mapTransactionsToPresentationModel(transactions)
            if (!sortAscending) transactionDataList.reversed() else transactionDataList
        }
    }

    private fun queryTransactions(
        teamId: String,
        startDate: Long?,
        endDate: Long?,
        sortAscending: Boolean,
    ): Flow<List<MyTeam>> {
        return teamDao.observeAll().map { entities ->
            entities.filter {
                it.teamId == teamId &&
                    it.docType == "transaction" &&
                    it.status != "archived" &&
                    (startDate == null || it.date >= startDate) &&
                    (endDate == null || it.date <= endDate)
            }.sortedByWithDirection(sortAscending) { it.date }
                .map { it.toRealmModel() }
        }
    }

    private fun mapTransactionsToPresentationModel(transactions: List<MyTeam>): List<Transaction> {
        val transactionDataList = mutableListOf<Transaction>()
        var balance = 0
        for (team in transactions) {
            val id = team._id ?: continue
            balance += if ("debit".equals(team.type, ignoreCase = true)) -team.amount else team.amount
            transactionDataList.add(
                Transaction(
                    id = id,
                    date = team.date,
                    description = team.description,
                    type = team.type,
                    amount = team.amount,
                    balance = balance,
                    imageName = team.imageName
                )
            )
        }
        return transactionDataList
    }

    override suspend fun createTransaction(
        teamId: String,
        type: String,
        note: String,
        amount: Int,
        date: Long,
        parentCode: String?,
        planetCode: String?,
        imageName: String?,
        imageData: ByteArray?,
    ): Result<Unit> {
        if (teamId.isBlank()) {
            return Result.failure(IllegalArgumentException("teamId cannot be blank"))
        }
        return runCatching {
            val transactionId = UUID.randomUUID().toString()
            val transaction = MyTeam().apply {
                _id = transactionId
                status = "active"
                this.date = date
                this.type = type
                description = note
                this.teamId = teamId
                this.amount = amount
                this.parentCode = parentCode
                teamPlanetCode = planetCode
                teamType = "sync"
                docType = "transaction"
                updated = true
            }
            teamDao.upsert(transaction.requireRoomEntity())
            if (imageName != null && imageData != null) {
                attachTeamImage(transactionId, imageName, imageData)
            }
        }
    }

    override suspend fun addReport(report: FinanceReportParams) {
        val reportId = UUID.randomUUID().toString()
        val doc = JsonObject().apply {
            addProperty("_id", reportId)
            addProperty("createdDate", timeProvider.now())
            addProperty("description", report.description)
            addProperty("beginningBalance", report.beginningBalance)
            addProperty("sales", report.sales)
            addProperty("otherIncome", report.otherIncome)
            addProperty("wages", report.wages)
            addProperty("otherExpenses", report.otherExpenses)
            addProperty("startDate", report.startDate)
            addProperty("endDate", report.endDate)
            addProperty("updatedDate", timeProvider.now())
            addProperty("teamId", report.teamId)
            addProperty("teamType", report.teamType)
            addProperty("teamPlanetCode", report.teamPlanetCode)
            addProperty("docType", "report")
            addProperty("updated", true)
        }
        val reportEntry = MyTeam().apply { _id = reportId }
        MyTeam.populateTeamFields(doc, reportEntry)
        teamDao.upsert(reportEntry.requireRoomEntity())
        if (report.imageName != null && report.imageData != null) {
            attachTeamImage(reportId, report.imageName, report.imageData)
        }
    }

    override suspend fun attachTeamImage(teamId: String, imageName: String, imageData: ByteArray) {
        if (teamId.isBlank()) return
        val destFile = MyTeam.getAttachmentFile(MainApplication.context, teamId, imageName) ?: return
        withContext(dispatcherProvider.io) {
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(imageData)
        }
        updateTeamEntityById(teamId) { team ->
            team.imageName = imageName
            team.updated = true
        }
    }

    override suspend fun updateReport(reportId: String, payload: FinanceReportParams) {
        if (reportId.isBlank()) return
        val doc = JsonObject().apply {
            addProperty("description", payload.description)
            addProperty("beginningBalance", payload.beginningBalance)
            addProperty("sales", payload.sales)
            addProperty("otherIncome", payload.otherIncome)
            addProperty("wages", payload.wages)
            addProperty("otherExpenses", payload.otherExpenses)
            addProperty("startDate", payload.startDate)
            addProperty("endDate", payload.endDate)
            addProperty("updatedDate", timeProvider.now())
            addProperty("updated", true)
        }
        updateTeamEntityById(reportId) { report ->
            MyTeam.populateReportFields(doc, report)
            report.updated = true
            if (report.updatedDate == 0L) {
                report.updatedDate = timeProvider.now()
            }
        }
        if (payload.imageName != null && payload.imageData != null) {
            attachTeamImage(reportId, payload.imageName, payload.imageData)
        }
    }

    override suspend fun archiveReport(reportId: String) {
        if (reportId.isBlank()) return
        updateTeamEntityById(reportId) { report ->
            report.status = "archived"
            report.updated = true
        }
    }

    override suspend fun isMember(userId: String?, teamId: String): Boolean {
        userId ?: return false
        return teamDao.getByTeamIdAndDocType(teamId, "membership")
            .any { it.userId == userId && !it.isDeletePending }
    }

    override suspend fun isTeamLeader(teamId: String, userId: String?): Boolean {
        if (teamId.isBlank() || userId.isNullOrBlank()) return false
        return teamDao.getByTeamIdAndDocType(teamId, "membership")
            .any { it.userId == userId && it.isLeader }
    }

    override suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean {
        if (teamId.isBlank() || userId.isNullOrBlank()) return false
        return teamDao.getByTeamIdUserIdAndDocType(teamId, userId, "request") != null
    }

    private suspend fun getTeamMemberStatuses(
        userId: String?,
        teamIds: Collection<String>,
    ): Map<String, TeamMemberStatus> {
        if (userId.isNullOrBlank() || teamIds.isEmpty()) return emptyMap()

        val validIds = teamIds.filter { it.isNotBlank() }.distinct()
        if (validIds.isEmpty()) return emptyMap()

        val userEntries = teamDao.getByUserId(userId)
            .filter { it.teamId in validIds && (it.docType == "membership" || it.docType == "request") }

        val membershipMap = userEntries
            .filter { it.docType == "membership" && !it.isDeletePending }
            .mapNotNull { it.teamId }
            .toSet()

        val leaderMap = userEntries
            .filter { it.docType == "membership" && it.isLeader }
            .mapNotNull { it.teamId }
            .toSet()

        val pendingRequestMap = userEntries
            .filter { it.docType == "request" }
            .mapNotNull { it.teamId }
            .toSet()

        return validIds.associateWith { teamId ->
            TeamMemberStatus(
                isMember = teamId in membershipMap,
                isLeader = teamId in leaderMap,
                hasPendingRequest = teamId in pendingRequestMap
            )
        }
    }

    private suspend fun getRecentVisitCounts(teamIds: Collection<String>): Map<String, Long> {
        if (teamIds.isEmpty()) return emptyMap()

        val validIds = teamIds.filter { it.isNotBlank() }.distinct()
        if (validIds.isEmpty()) return emptyMap()

        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
        val recentLogs = teamLogDao.getRecentTeamVisits(cutoff, validIds)

        return recentLogs.mapNotNull { it.teamId }
            .groupingBy { it }
            .eachCount()
            .mapValues { it.value.toLong() }
    }

    override suspend fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?) {
        if (teamId.isBlank() || userId.isNullOrBlank()) return
        val membership = teamDao.getByTeamIdUserIdAndDocType(teamId, userId, "membership")
        if (membership?.isDeletePending == true) {
            val updatedMembership = membership.toRealmModel().apply {
                isDeletePending = false
                updated = false
            }
            teamDao.upsert(updatedMembership.requireRoomEntity())
            return
        }

        val request = MyTeam().apply {
            _id = AndroidDecrypter.generateIv()
            docType = "request"
            createdDate = Date().time
            this.teamType = teamType
            this.userId = userId
            this.teamId = teamId
            updated = true
            teamPlanetCode = userPlanetCode
            this.userPlanetCode = userPlanetCode
        }
        teamDao.upsert(request.requireRoomEntity())
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
            val request = teamDao.getByTeamIdUserIdAndDocType(teamId, userId, "request")
                ?: throw IllegalStateException("Request not found for user $userId")

            if (accept) {
                val accepted = request.toRealmModel().apply {
                    docType = "membership"
                    updated = true
                }
                teamDao.upsert(accepted.requireRoomEntity())
            } else {
                teamDao.deleteById(request._id ?: request.id)
            }
        }
    }

    override suspend fun leaveTeam(teamId: String, userId: String?) {
        if (teamId.isBlank() || userId.isNullOrBlank()) return
        markMembershipsForLeave(teamId, userId)
    }

    override suspend fun removeMember(teamId: String, userId: String) {
        if (teamId.isBlank() || userId.isBlank()) return
        markMembershipsForLeave(teamId, userId)
    }

    override suspend fun addResourceLinks(
        teamId: String,
        resources: List<TeamResourceDto>,
        userId: String?,
    ) {
        if (teamId.isBlank() || resources.isEmpty() || userId.isNullOrBlank()) return

        val user = userDao.getById(userId)?.toRealmModel() ?: return
        val teamResources = resources.mapNotNull { resource ->
            MyTeam().apply {
                _id = UUID.randomUUID().toString()
                this.teamId = teamId
                title = resource.title
                status = user.parentCode
                resourceId = resource.resourceId
                docType = "resourceLink"
                updated = true
                teamType = "local"
                teamPlanetCode = user.planetCode
                userPlanetCode = user.planetCode
            }.toRoomEntity()
        }

        if (teamResources.isNotEmpty()) {
            teamDao.upsertAll(teamResources)
        }
    }

    override suspend fun removeResourceLink(teamId: String, resourceId: String) {
        if (teamId.isBlank() || resourceId.isBlank()) return
        val teamResource = teamDao.getAll().firstOrNull {
            it.teamId == teamId && it.resourceId == resourceId && it.docType == "resourceLink"
        } ?: return
        val updatedResource = teamResource.toRealmModel().apply {
            this.resourceId = ""
            updated = true
        }
        teamDao.upsert(updatedResource.requireRoomEntity())
    }

    override suspend fun getPendingTasksForUser(
        userId: String,
        start: Long,
        end: Long,
    ): List<TeamTask> {
        if (userId.isBlank() || start > end) return emptyList()
        return teamTaskDao.getPendingTasksForUser(userId, start, end)
    }

    override suspend fun markTasksNotified(taskIds: Collection<String>) {
        if (taskIds.isEmpty()) return
        val validIds = taskIds.mapNotNull { it.takeIf(String::isNotBlank) }.distinct()
        if (validIds.isEmpty()) return
        teamTaskDao.markTasksNotified(validIds)
    }

    override suspend fun getTasksByTeamId(teamId: String): Flow<List<TeamTask>> {
        return teamTaskDao.getTasksByTeamId(teamId)
    }

    override suspend fun getReportsFlow(teamId: String): Flow<List<MyTeam>> {
        return teamDao.observeAll().map { entities ->
            entities.filter {
                it.teamId == teamId &&
                    it.docType == "report" &&
                    it.status != "archived"
            }.sortedByDescending { it.createdDate }
                .map { it.toRealmModel() }
        }
    }

    override suspend fun exportReportsAsCsv(reports: List<MyTeam>, teamName: String): String {
        val csvBuilder = StringBuilder()
        csvBuilder.append("$teamName Financial Report Summary\n\n")
        csvBuilder.append("Start Date, End Date, Created Date, Updated Date, Beginning Balance, Sales, Other Income, Wages, Other Expenses, Profit/Loss, Ending Balance\n")
        for (report in reports) {
            val totalIncome = report.sales + report.otherIncome
            val totalExpenses = report.wages + report.otherExpenses
            val profitLoss = totalIncome - totalExpenses
            val endingBalance = profitLoss + report.beginningBalance
            csvBuilder.append("${org.ole.planet.myplanet.utils.TimeUtils.formatDateForCsv(report.startDate)}, ${org.ole.planet.myplanet.utils.TimeUtils.formatDateForCsv(report.endDate)}, ${org.ole.planet.myplanet.utils.TimeUtils.formatDateForCsv(report.createdDate)}, ${org.ole.planet.myplanet.utils.TimeUtils.formatDateForCsv(report.updatedDate)}, ${report.beginningBalance}, ${report.sales}, ${report.otherIncome}, ${report.wages}, ${report.otherExpenses}, $profitLoss, $endingBalance\n")
        }
        return csvBuilder.toString()
    }

    override suspend fun deleteTask(taskId: String) {
        teamTaskDao.deleteById(taskId)
    }

    private suspend fun upsertTask(task: TeamTask) {
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
        teamTaskDao.upsert(task)
    }

    override suspend fun createTask(
        title: String,
        description: String,
        deadline: Long,
        teamId: String,
        assigneeId: String?,
    ) {
        val teamTask = TeamTask().apply {
            id = UUID.randomUUID().toString()
            this.title = title
            this.description = description
            this.deadline = deadline
            this.teamId = teamId
            assignee = assigneeId
            isUpdated = true
        }
        upsertTask(teamTask)
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        deadline: Long,
        assigneeId: String?,
    ) {
        teamTaskDao.getById(taskId)?.let { task ->
            task.title = title
            task.description = description
            task.deadline = deadline
            task.assignee = assigneeId
            task.isUpdated = true
            teamTaskDao.upsert(task)
        }
    }

    override suspend fun assignTask(taskId: String, assigneeId: String?) {
        teamTaskDao.getById(taskId)?.let { task ->
            task.assignee = assigneeId
            task.isUpdated = true
            teamTaskDao.upsert(task)
        }
    }

    override suspend fun setTaskCompletion(taskId: String, completed: Boolean) {
        teamTaskDao.getById(taskId)?.let { task ->
            task.completed = completed
            task.completedTime = if (completed) Date().time else 0
            task.isUpdated = true
            teamTaskDao.upsert(task)
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
        val log = TeamLog().apply {
            id = UUID.randomUUID().toString()
            this.teamId = teamId
            user = userName
            createdOn = userPlanetCode
            type = "teamVisit"
            this.teamType = teamType
            parentCode = userParentCode
            time = Date().time
        }
        teamLogDao.insert(log)
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
            val team = getTeamEntityByAnyId(teamId)?.toRealmModel() ?: return@runCatching false
            team.name = name
            team.services = services
            team.rules = rules
            team.description = description
            updatedBy?.let { team.createdBy = it }
            team.limit = 12
            team.updated = true
            teamDao.upsert(team.requireRoomEntity())
            true
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
        val team = getTeamEntityByAnyId(teamId)?.toRealmModel() ?: return false
        team.name = name
        team.description = description
        team.services = services
        team.rules = rules
        team.teamType = teamType
        team.isPublic = isPublic
        team.createdBy = createdBy.takeIf { it.isNotBlank() } ?: team.createdBy
        team.updated = true
        teamDao.upsert(team.requireRoomEntity())
        return true
    }

    override suspend fun syncTeamActivities() {
        val updateUrl = sharedPrefManager.getServerUrl()
        val mapping = serverUrlMapper.processUrl(updateUrl)

        val primaryAvailable = MainApplication.isServerReachable(mapping.primaryUrl)
        val alternativeAvailable =
            mapping.alternativeUrl?.let { MainApplication.isServerReachable(it) } == true

        if (!primaryAvailable && alternativeAvailable) {
            mapping.alternativeUrl.let { alternativeUrl ->
                val uri = updateUrl.toUri()
                val editor = preferences.edit()
                serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, preferences)
            }
        }

        uploadTeamActivities()
    }

    private suspend fun uploadTeamActivities() {
        try {
            withContext(dispatcherProvider.io) {
                uploadManager.uploadResource(null)
                uploadManager.uploadTeams()
                uploadManager.uploadTeamActivities()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun getResourceIds(teamId: String): List<String> {
        return teamDao.getAll().filter {
            it.teamId == teamId &&
                it.resourceId?.isNotBlank() == true &&
                (it.docType.isNullOrBlank() || it.docType == "resourceLink" || it.docType == "link")
        }.mapNotNull { it.resourceId }
    }

    override suspend fun getTeamType(teamId: String): String? {
        if (teamId.isBlank()) return null
        return teamDao.getById(teamId)?.type
    }

    override suspend fun refreshJoinedMembersForLogin(teamId: String): List<UserEntity> = withContext(dispatcherProvider.io) {
        val teamMembers = getJoinedMembers(teamId)
        val userList = teamMembers.map {
            User(it.name ?: "", it.name ?: "", "", it.userImage ?: "", "team")
        }

        val existingUsers = sharedPrefManager.getSavedUsers().toMutableList()
        val filteredExistingUsers = existingUsers.filter { it.source != "team" }
        val existingNames = filteredExistingUsers.mapTo(HashSet()) { it.name }
        val updatedUserList = userList.filterNot { user -> user.name in existingNames } + filteredExistingUsers
        sharedPrefManager.setSavedUsers(updatedUserList)

        teamMembers
    }

    override suspend fun getJoinedMembers(teamId: String): List<UserEntity> {
        val teamMembers = teamDao.getByTeamIdAndDocType(teamId, "membership")
            .mapNotNull { it.userId }
            .distinct()

        return teamMembers.mapNotNull { userDao.getById(it)?.toRealmModel() }
    }

    override suspend fun getJoinedMembersWithVisitInfo(teamId: String): List<JoinedMemberData> {
        data class MemberStats(
            val member: UserEntity,
            val visitCount: Long,
            val lastVisitTimestamp: Long?,
            val isLeader: Boolean,
        )

        val members = getJoinedMembers(teamId).toMutableList()
        val communityLeadersJson = sharedPrefManager.getCommunityLeaders()

        if (communityLeadersJson.isNotEmpty()) {
            val adminUsers = userRepository.parseLeadersJson(communityLeadersJson)
            val teamUserIds = teamDao.getAll().filter { it.teamId == teamId }.mapNotNull { it.userId }.toSet()
            val memberNames = members.mapTo(HashSet()) { it.name }
            val validAdmins = adminUsers.filter { admin ->
                val adminFullId = "org.couchdb.user:${admin.name}"
                adminFullId in teamUserIds && admin.name !in memberNames && !admin.name.isNullOrBlank()
            }

            if (validAdmins.isNotEmpty()) {
                val adminFromRoomMap = validAdmins.mapNotNull { admin ->
                    admin.name?.let { name -> userDao.getByName(name)?.toRealmModel()?.let { name to it } }
                }.toMap()

                for (admin in validAdmins) {
                    members.add(adminFromRoomMap[admin.name] ?: admin)
                }
            }
        }

        val leaderIds = teamDao.getByTeamIdAndDocType(teamId, "membership")
            .filter { it.isLeader }
            .mapNotNull { it.userId }
            .toSet()

        val leaders = mutableListOf<UserEntity>()
        val nonLeaders = mutableListOf<UserEntity>()
        members.forEach { member ->
            if (member.id in leaderIds) {
                leaders.add(member)
            } else {
                nonLeaders.add(member)
            }
        }

        val orderedMembers = leaders + nonLeaders
        val userNames = orderedMembers.mapNotNull { it.name }.distinct()
        val logs = if (userNames.isNotEmpty()) {
            teamLogDao.getTeamVisitsForUsers(teamId, userNames)
        } else {
            emptyList()
        }

        val visitCounts = logs.groupingBy { it.user }.eachCount()
        val lastVisits = logs.groupBy { it.user }.mapValues { (_, userLogs) -> userLogs.maxOfOrNull { it.time ?: 0 } }
        val formatter = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault())

        return orderedMembers.map { member ->
            val visitCount = visitCounts[member.name]?.toLong() ?: 0L
            val lastVisitTimestamp = lastVisits[member.name]
            val lastLogoutTimestamp = activitiesRepository.getLastVisit(member.name ?: "")
            val profileLastVisit = if (lastLogoutTimestamp != null) {
                formatter.format(Date(lastLogoutTimestamp))
            } else {
                "No logout record found"
            }
            val offlineVisits = "${member.id?.let { activitiesRepository.getOfflineVisitCount(it) } ?: 0}"
            JoinedMemberData(
                user = member,
                visitCount = visitCount,
                lastVisitDate = lastVisitTimestamp,
                offlineVisits = offlineVisits,
                profileLastVisit = profileLastVisit,
                isLeader = member.id in leaderIds
            )
        }
    }

    override suspend fun getJoinedMemberCount(teamId: String): Int {
        return teamDao.countByTeamIdAndDocType(teamId, "membership")
    }

    override suspend fun getAssignee(userId: String): UserEntity? {
        return userDao.getById(userId)?.toRealmModel()
    }

    override suspend fun getRequestedMembers(teamId: String): List<UserEntity> {
        val requestedMemberIds = teamDao.getByTeamIdAndDocType(teamId, "request")
            .mapNotNull { it.userId }
            .distinct()
        return requestedMemberIds.mapNotNull { userDao.getById(it)?.toRealmModel() }
    }

    override suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String?): Boolean {
        if (name.isBlank()) return false
        return teamDao.getAll().any {
            it.name.equals(name, ignoreCase = true) &&
                it.type == type &&
                it.isRootTeam() &&
                it.status != "archived" &&
                (excludeTeamId == null || (it._id ?: it.id) != excludeTeamId)
        }
    }

    override suspend fun updateTeamLeader(teamId: String, newLeaderId: String): Boolean {
        val memberships = teamDao.getByTeamIdAndDocType(teamId, "membership")
        val newLeader = memberships.firstOrNull { it.userId == newLeaderId } ?: return false
        val updatedMemberships = memberships.map { membership ->
            membership.toRealmModel().apply {
                isLeader = userId == newLeader.userId
            }.requireRoomEntity()
        }
        teamDao.upsertAll(updatedMemberships)
        return true
    }

    override suspend fun getNextLeaderCandidate(teamId: String, excludeUserId: String?): UserEntity? {
        val members = teamDao.getByTeamIdAndDocType(teamId, "membership").filter {
            !it.isLeader &&
                it.status != "archived" &&
                (excludeUserId == null || it.userId != excludeUserId)
        }
        if (members.isEmpty()) return null

        val users = members.mapNotNull { member ->
            member.userId?.let { userId -> userDao.getById(userId)?.toRealmModel() }
        }
        if (users.isEmpty()) return null

        val userMap = users.associateBy { it.id }
        val userNames = users.mapNotNull { it.name }.distinct()
        val logs = if (userNames.isNotEmpty()) {
            teamLogDao.getTeamVisitsForUsers(teamId, userNames)
        } else {
            emptyList()
        }
        val visitCounts = logs.groupingBy { it.user }.eachCount()

        val successorMember = members.maxByOrNull { member ->
            userMap[member.userId]?.name?.let { name -> visitCounts[name]?.toLong() ?: 0L } ?: 0L
        }
        return successorMember?.userId?.let { userMap[it] }
    }

    override suspend fun getTeamCreator(teamId: String): String? {
        if (teamId.isBlank()) return null
        return teamDao.getById(teamId)?.userId
    }

    override suspend fun getAvailableResourcesToAdd(teamId: String): List<MyLibrary> {
        val existing = getTeamResources(teamId)
        val existingIds = existing.mapNotNull { it._id }
        val allLibraryItems = resourcesRepositoryLazy.get().getPublicLibraryItems()
        return allLibraryItems.filter { it._id !in existingIds }
    }

    override suspend fun insertTeamLog(json: JsonObject) {
        teamLogDao.upsertAll(listOf(teamLogFromJson(json)))
    }

    override suspend fun insertTeamLogs(logs: List<JsonObject>) {
        teamLogDao.upsertAll(logs.map(::teamLogFromJson))
    }

    override suspend fun getLastVisit(userName: String?, teamId: String?): Long? {
        return teamLogDao.getLastVisit(userName, teamId)
    }

    private fun teamLogFromJson(json: JsonObject): TeamLog {
        val remoteId = JsonUtils.getString("_id", json)
        return TeamLog().apply {
            id = remoteId
            _rev = JsonUtils.getString("_rev", json)
            _id = remoteId
            type = JsonUtils.getString("type", json)
            user = JsonUtils.getString("user", json)
            createdOn = JsonUtils.getString("createdOn", json)
            parentCode = JsonUtils.getString("parentCode", json)
            time = JsonUtils.getLong("time", json)
            teamId = JsonUtils.getString("teamId", json)
            teamType = JsonUtils.getString("teamType", json)
        }
    }

    override fun serializeTeamActivities(log: TeamLog, context: Context): JsonObject {
        val ob = JsonObject()
        ob.addProperty("user", log.user)
        ob.addProperty("type", log.type)
        ob.addProperty("createdOn", log.createdOn)
        ob.addProperty("parentCode", log.parentCode)
        ob.addProperty("teamType", log.teamType)
        ob.addProperty("time", log.time)
        ob.addProperty("teamId", log.teamId)
        ob.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
        ob.addProperty("deviceName", NetworkUtils.getDeviceName())
        ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
        if (!TextUtils.isEmpty(log._rev)) {
            ob.addProperty("_rev", log._rev)
            ob.addProperty("_id", log._id)
        }
        return ob
    }

    private fun processDescription(description: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val links = DownloadUtils.extractLinks(description ?: "")
        val baseUrl = UrlUtils.getUrl()
        val concatenatedLinks = LinkedHashSet<String>()
        for (link in links) {
            concatenatedLinks.add("$baseUrl/$link")
        }
        DownloadUtils.openDownloadService(MainApplication.context, ArrayList(concatenatedLinks), true)
    }

    override suspend fun batchInsertMyTeams(documents: List<JsonObject>): Int {
        var processedCount = 0
        try {
            val validDocuments = documents.filter { doc ->
                val id = JsonUtils.getString("_id", doc)
                id.isNotEmpty() && !id.startsWith("_design")
            }
            val ids = validDocuments.map { JsonUtils.getString("_id", it) }
            val existingTeams = teamDao.getAll()
                .filter { (it._id ?: it.id) in ids }
                .associateBy { it._id ?: it.id }
                .toMutableMap()

            validDocuments.forEach { doc ->
                try {
                    insertMyTeam(doc, existingTeams)
                    processedCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return processedCount
    }

    override suspend fun insertMyTeam(doc: JsonObject) {
        insertMyTeam(doc, null)
    }

    private suspend fun insertMyTeam(doc: JsonObject, existingTeams: MutableMap<String, RoomTeamEntity>?) {
        val status = JsonUtils.getString("status", doc)
        if (status == "archived") return

        val teamId = JsonUtils.getString("_id", doc)
        if (teamId.isBlank()) return

        val docType = JsonUtils.getString("docType", doc)
        val userId = JsonUtils.getString("userId", doc)
        val teamIdField = JsonUtils.getString("teamId", doc)

        if (docType == "membership" && userId.isNotBlank() && teamIdField.isNotBlank()) {
            teamDao.deleteByTeamIdUserIdAndDocType(teamIdField, userId, "request")
        } else if (docType == "request" && userId.isNotBlank() && teamIdField.isNotBlank()) {
            val alreadyMember = teamDao.countByTeamIdUserIdAndDocType(teamIdField, userId, "membership") > 0
            if (alreadyMember) return
        }

        val existingTeam = existingTeams?.get(teamId) ?: teamDao.getById(teamId)
        val model = existingTeam?.toRealmModel() ?: MyTeam().apply { _id = teamId }
        MyTeam.populateTeamFields(doc, model, true)
        processDescription(model.description)
        val entity = model.requireRoomEntity()
        teamDao.upsert(entity)
        existingTeams?.put(teamId, entity)
    }

    override suspend fun bulkInsertFromSync(jsonArray: JsonArray) {
        val documentList = ArrayList<JsonObject>(jsonArray.size())
        val ids = mutableListOf<String>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
                ids.add(id)
            }
        }
        val existingTeams = teamDao.getAll()
            .filter { (it._id ?: it.id) in ids }
            .associateBy { it._id ?: it.id }
            .toMutableMap()
        documentList.forEach { jsonDoc ->
            insertMyTeam(jsonDoc, existingTeams)
        }
    }

    override suspend fun bulkInsertTasksFromSync(jsonArray: JsonArray) {
        val tasks = ArrayList<TeamTask>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                tasks.add(TeamTask.fromJson(jsonDoc))
            }
        }
        teamTaskDao.upsertAll(tasks)
    }

    override suspend fun bulkInsertTeamActivitiesFromSync(jsonArray: JsonArray) {
        val documentList = ArrayList<JsonObject>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        insertTeamLogs(documentList)
    }

    private suspend fun getCoursesForSerialization(courseIds: List<String>): List<org.ole.planet.myplanet.model.MyCourse> {
        if (courseIds.isEmpty()) return emptyList()
        val stepsByCourseId = courseStepDao.getByCourseIds(courseIds)
            .groupBy { it.courseId }
            .mapValues { (_, steps) -> steps.map { it.toRealmModel() } }
        return courseDao.getByCourseIds(courseIds).map { courseEntity ->
            courseEntity.toRealmModel(stepsByCourseId[courseEntity.courseId].orEmpty())
        }
    }

    private suspend fun getTeamEntityByAnyId(id: String): RoomTeamEntity? {
        return teamDao.getById(id) ?: teamDao.getByTeamId(id)
    }

    private suspend fun updateTeamEntityById(id: String, updater: (MyTeam) -> Unit): Boolean {
        val entity = teamDao.getById(id) ?: return false
        val model = entity.toRealmModel()
        updater(model)
        teamDao.upsert(model.requireRoomEntity())
        return true
    }

    private suspend fun markMembershipsForLeave(teamId: String, userId: String) {
        val memberships = teamDao.getByTeamIdAndDocType(teamId, "membership")
            .filter { it.userId == userId }
        memberships.forEach { membership ->
            if (membership._rev.isNullOrBlank()) {
                teamDao.deleteById(membership._id ?: membership.id)
            } else {
                val updatedMembership = membership.toRealmModel().apply {
                    isDeletePending = true
                    updated = true
                }
                teamDao.upsert(updatedMembership.requireRoomEntity())
            }
        }
    }

    private fun MyTeam.requireRoomEntity(): RoomTeamEntity {
        return requireNotNull(toRoomEntity()) { "Failed to convert team to Room entity" }
    }

    private fun MyTeam.toSummary(): TeamSummary? {
        val id = _id ?: return null
        return TeamSummary(
            _id = id,
            name = name ?: "",
            teamType = teamType,
            teamPlanetCode = teamPlanetCode,
            createdDate = createdDate,
            type = type,
            status = status,
            teamId = teamId,
            description = description,
            services = services,
            rules = rules
        )
    }

    private fun RoomTeamEntity.isRootTeam(): Boolean = teamId.isNullOrBlank()

    private fun <T : Comparable<T>> List<RoomTeamEntity>.sortedByWithDirection(
        ascending: Boolean,
        selector: (RoomTeamEntity) -> T,
    ): List<RoomTeamEntity> {
        return if (ascending) sortedBy(selector) else sortedByDescending(selector)
    }
}
