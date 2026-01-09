package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.Transaction

data class JoinedMemberData(
    val user: RealmUserModel,
    val visitCount: Long,
    val lastVisitDate: Long?,
    val offlineVisits: String,
    val profileLastVisit: String,
    var isLeader: Boolean
)

data class TeamMemberStatus(
    val isMember: Boolean,
    val isLeader: Boolean,
    val hasPendingRequest: Boolean
)

data class JoinRequestNotification(
    val requesterName: String,
    val teamName: String,
    val requestId: String
)

interface TeamsRepository {
    suspend fun getMyTeamsFlow(userId: String): Flow<List<RealmMyTeam>>
    suspend fun getShareableTeams(): List<RealmMyTeam>
    suspend fun getShareableEnterprises(): List<RealmMyTeam>
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun getTeamByDocumentIdOrTeamId(id: String): RealmMyTeam?
    suspend fun getTeamLinks(): List<RealmMyTeam>
    suspend fun getTeamById(teamId: String): RealmMyTeam?
    suspend fun getTaskTeamInfo(taskId: String): Triple<String, String, String>?
    suspend fun getJoinRequestTeamId(requestId: String): String?
    suspend fun getTaskNotifications(userId: String?): List<Triple<String, String, String>>
    suspend fun getJoinRequestNotifications(userId: String?): List<JoinRequestNotification>
    suspend fun getTasksFlow(userId: String?): Flow<List<RealmTeamTask>>
    suspend fun getTasks(userId: String?): List<RealmTeamTask>
    suspend fun isMember(userId: String?, teamId: String): Boolean
    suspend fun isTeamLeader(teamId: String, userId: String?): Boolean
    suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean
    suspend fun getTeamMemberStatuses(userId: String?, teamIds: Collection<String>): Map<String, TeamMemberStatus>
    suspend fun getRecentVisitCounts(teamIds: Collection<String>): Map<String, Long>
    suspend fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?)
    suspend fun leaveTeam(teamId: String, userId: String?)
    suspend fun removeMember(teamId: String, userId: String)
    suspend fun addResourceLinks(teamId: String, resources: List<RealmMyLibrary>, user: RealmUserModel?)
    suspend fun removeResourceLink(teamId: String, resourceId: String)
    suspend fun deleteTask(taskId: String)
    suspend fun upsertTask(task: RealmTeamTask)
    suspend fun createTask(title: String, description: String, deadline: Long, teamId: String, assigneeId: String?)
    suspend fun updateTask(taskId: String, title: String, description: String, deadline: Long, assigneeId: String?)
    suspend fun assignTask(taskId: String, assigneeId: String?)
    suspend fun setTaskCompletion(taskId: String, completed: Boolean)
    suspend fun getPendingTasksForUser(userId: String, start: Long, end: Long): List<RealmTeamTask>
    suspend fun markTasksNotified(taskIds: Collection<String>)
    suspend fun getTasksByTeamId(teamId: String): Flow<List<RealmTeamTask>>
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
    suspend fun createTeam(
        category: String?,
        name: String,
        description: String,
        services: String,
        rules: String,
        teamType: String?,
        isPublic: Boolean,
        user: RealmUserModel,
    ): Result<String>
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
    @Deprecated("Use getTeamTransactionsWithBalance instead", ReplaceWith("getTeamTransactionsWithBalance(teamId, startDate, endDate, sortAscending)"))
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
    suspend fun respondToMemberRequest(teamId: String, userId: String, accept: Boolean): Result<Unit>
    suspend fun getJoinedMembers(teamId: String): List<RealmUserModel>
    suspend fun getJoinedMembersWithVisitInfo(teamId: String): List<JoinedMemberData>
    suspend fun getJoinedMemberCount(teamId: String): Int
    suspend fun getAssignee(userId: String): RealmUserModel?
    suspend fun getRequestedMembers(teamId: String): List<RealmUserModel>
    suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String? = null): Boolean
}
