package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import androidx.core.net.toUri
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.api.ApiClient.client
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.JsonUtils

class TeamRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val uploadManager: UploadManager,
    @AppPreferences private val preferences: SharedPreferences,
    private val serverUrlMapper: ServerUrlMapper,
    private val teamMapper: TeamMapper
) : RealmRepository(databaseService), TeamRepository {

    override suspend fun getMyTeamsFlow(userId: String): Flow<List<RealmMyTeam>> {
        return queryListFlow(RealmMyTeam::class.java) {
            equalTo("userId", userId)
            equalTo("docType", "membership")
        }.flatMapLatest { memberships ->
            val teamIds = memberships.mapNotNull { it.teamId }.toTypedArray()
            if (teamIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                queryListFlow(RealmMyTeam::class.java) {
                    `in`("_id", teamIds)
                    notEqualTo("status", "archived")
                }
            }
        }
    }

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

    override suspend fun getTeamByIdOrTeamId(id: String): RealmMyTeam? {
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

    override suspend fun getTeamTransactions(
        teamId: String,
        startDate: Long?,
        endDate: Long?,
        sortAscending: Boolean,
    ): Flow<List<RealmMyTeam>> {
        return queryTransactions(teamId, startDate, endDate, sortAscending)
    }

    override suspend fun getTeamTransactionsWithBalance(
        teamId: String,
        startDate: Long?,
        endDate: Long?,
        sortAscending: Boolean,
    ): Flow<List<Transaction>> {
        return queryTransactions(teamId, startDate, endDate, true).map { transactions ->
            val transactionDataList = teamMapper.mapTransactionsToPresentationModel(transactions)
            if (!sortAscending) {
                transactionDataList.reversed()
            } else {
                transactionDataList
            }
        }
    }

    private suspend fun queryTransactions(
        teamId: String,
        startDate: Long?,
        endDate: Long?,
        sortAscending: Boolean
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

    override suspend fun getReportsFlow(teamId: String): Flow<List<RealmMyTeam>> {
        return queryListFlow(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
            equalTo("docType", "report")
            notEqualTo("status", "archived")
            sort("createdDate", io.realm.Sort.DESCENDING)
        }
    }

    override suspend fun exportReportsAsCsv(reports: List<RealmMyTeam>, teamName: String): String {
        val csvBuilder = StringBuilder()
        csvBuilder.append("$teamName Financial Report Summary\n\n")
        csvBuilder.append("Start Date, End Date, Created Date, Updated Date, Beginning Balance, Sales, Other Income, Wages, Other Expenses, Profit/Loss, Ending Balance\n")
        for (report in reports) {
            val dateFormat = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)", Locale.US)
            val totalIncome = report.sales + report.otherIncome
            val totalExpenses = report.wages + report.otherExpenses
            val profitLoss = totalIncome - totalExpenses
            val endingBalance = profitLoss + report.beginningBalance
            csvBuilder.append("${dateFormat.format(report.startDate)}, ${dateFormat.format(report.endDate)}, ${dateFormat.format(report.createdDate)}, ${dateFormat.format(report.updatedDate)}, ${report.beginningBalance}, ${report.sales}, ${report.otherIncome}, ${report.wages}, ${report.otherExpenses}, $profitLoss, $endingBalance\n")
        }
        return csvBuilder.toString()
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

    override suspend fun createTeamAndAddMember(teamObject: JsonObject, user: RealmUserModel): Result<String> {
        return runCatching {
            val teamId = AndroidDecrypter.generateIv()
            executeTransaction { realm ->
                val team = realm.createObject(RealmMyTeam::class.java, teamId)
                team.status = "active"
                team.createdDate = Date().time
                val category = JsonUtils.getString("category", teamObject)
                if (category == "enterprise") {
                    team.type = "enterprise"
                    team.services = JsonUtils.getString("services", teamObject)
                    team.rules = JsonUtils.getString("rules", teamObject)
                } else {
                    team.type = "team"
                    team.teamType = JsonUtils.getString("teamType", teamObject)
                }
                team.name = JsonUtils.getString("name", teamObject)
                team.description = JsonUtils.getString("description", teamObject)
                team.createdBy = user._id
                team.teamId = ""
                team.isPublic = teamObject.get("isPublic")?.asBoolean ?: false
                team.userId = user._id
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
                membership.teamType = JsonUtils.getString("teamType", teamObject)
                membership.updated = true
            }
            teamId
        }
    }

    override suspend fun createEnterprise(
        name: String,
        description: String,
        services: String,
        rules: String,
        isPublic: Boolean,
        user: RealmUserModel,
    ): Result<String> {
        return runCatching {
            val enterpriseId = AndroidDecrypter.generateIv()
            executeTransaction { realm ->
                val enterprise = realm.createObject(RealmMyTeam::class.java, enterpriseId)
                enterprise.status = "active"
                enterprise.createdDate = Date().time
                enterprise.type = "enterprise"
                enterprise.services = services
                enterprise.rules = rules
                enterprise.name = name
                enterprise.description = description
                enterprise.createdBy = user._id
                enterprise.teamId = ""
                enterprise.isPublic = isPublic
                enterprise.userId = user.id
                enterprise.parentCode = user.parentCode
                enterprise.teamPlanetCode = user.planetCode
                enterprise.updated = true

                val membershipId = AndroidDecrypter.generateIv()
                val membership = realm.createObject(RealmMyTeam::class.java, membershipId)
                membership.userId = user._id
                membership.teamId = enterpriseId
                membership.teamPlanetCode = user.planetCode
                membership.userPlanetCode = user.planetCode
                membership.docType = "membership"
                membership.isLeader = true
                membership.teamType = "enterprise"
                membership.updated = true
            }
            enterpriseId
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

    override suspend fun syncTeamActivities() {
        val updateUrl = preferences.getString("serverURL", "") ?: ""
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
            val apiInterface = client.create(ApiInterface::class.java)
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

    override suspend fun getTeamType(teamId: String): String? {
        if (teamId.isBlank()) return null
        return findByField(RealmMyTeam::class.java, "_id", teamId)?.type
    }

    override suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String?): Boolean {
        if (name.isBlank()) return false

        return withRealm { realm ->
            val query = realm.where(RealmMyTeam::class.java)
                .equalTo("name", name, io.realm.Case.INSENSITIVE)
                .equalTo("type", type)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")

            excludeTeamId?.let {
                query.notEqualTo("_id", it)
            }

            query.count() > 0
        }
    }
}
