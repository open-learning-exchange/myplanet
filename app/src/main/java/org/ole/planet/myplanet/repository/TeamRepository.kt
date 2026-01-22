package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.Transaction

interface TeamRepository {
    suspend fun getMyTeamsFlow(userId: String): Flow<List<RealmMyTeam>>
    suspend fun getShareableTeams(): List<RealmMyTeam>
    suspend fun getShareableEnterprises(): List<RealmMyTeam>
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun getTeamByDocumentIdOrTeamId(id: String): RealmMyTeam?
    suspend fun getTeamByIdOrTeamId(id: String): RealmMyTeam?
    suspend fun getTeamLinks(): List<RealmMyTeam>
    suspend fun getTeamById(teamId: String): RealmMyTeam?
    suspend fun getTeamTransactions(
        teamId: String,
        startDate: Long? = null,
        endDate: Long? = null,
        sortAscending: Boolean = false,
    ): Flow<List<RealmMyTeam>>
    suspend fun getTeamTransactionsWithBalance(
        teamId: String,
        startDate: Long? = null,
        endDate: Long? = null,
        sortAscending: Boolean = false,
    ): Flow<List<Transaction>>
    suspend fun createTransaction(
        teamId: String,
        type: String,
        note: String,
        amount: Int,
        date: Long,
        parentCode: String?,
        planetCode: String?,
    ): Result<Unit>
    suspend fun getReportsFlow(teamId: String): Flow<List<RealmMyTeam>>
    suspend fun exportReportsAsCsv(reports: List<RealmMyTeam>, teamName: String): String
    suspend fun addReport(report: JsonObject)
    suspend fun updateReport(reportId: String, payload: JsonObject)
    suspend fun archiveReport(reportId: String)
    suspend fun logTeamVisit(
        teamId: String,
        userName: String?,
        userPlanetCode: String?,
        userParentCode: String?,
        teamType: String?,
    )

    suspend fun createTeamAndAddMember(teamObject: JsonObject, user: RealmUserModel): Result<String>
    suspend fun updateTeam(
        teamId: String,
        name: String,
        description: String,
        services: String,
        rules: String,
        updatedBy: String?,
    ): Result<Boolean>
    suspend fun updateTeamDetails(
        teamId: String,
        name: String,
        description: String,
        services: String,
        rules: String,
        teamType: String,
        isPublic: Boolean,
        createdBy: String,
    ): Boolean
    suspend fun syncTeamActivities()
    suspend fun getTeamType(teamId: String): String?
    suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String? = null): Boolean
    suspend fun createEnterprise(
        name: String,
        description: String,
        services: String,
        rules: String,
        isPublic: Boolean,
        user: RealmUserModel,
    ): Result<String>
}
