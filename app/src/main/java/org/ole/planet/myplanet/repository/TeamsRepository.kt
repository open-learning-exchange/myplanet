package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.CreateTeamRequest
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.model.Transaction

data class JoinedMemberData(
    val user: RealmUser,
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

data class TeamUploadData(
    val teamId: String?,
    val serialized: JsonObject
)

interface TeamsRepository {
    suspend fun getTeamsForUpload(): List<TeamUploadData>
    suspend fun markTeamUploaded(teamId: String?, rev: String)
    suspend fun getAllActiveTeams(): List<RealmMyTeam>
    suspend fun getMyTeamsFlow(userId: String): Flow<List<RealmMyTeam>>
    suspend fun getMyTeamsByUserId(userId: String): List<RealmMyTeam>
    suspend fun getResourceIds(teamId: String): List<String>
    suspend fun getResourceIdsByUser(userId: String?): List<String>
    suspend fun getTeamSummaries(userId: String?): List<TeamSummary>
    suspend fun getShareableEnterprises(): List<RealmMyTeam>
    suspend fun getShareableEnterpriseSummaries(userId: String?): List<TeamSummary>
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun getTeamCourseIds(teamId: String): List<String>
    suspend fun addCoursesToTeam(teamId: String, courseIds: List<String>): Result<Unit>
    suspend fun getTeamByDocumentIdOrTeamId(id: String): RealmMyTeam?
    suspend fun getTeamByIdOrTeamId(id: String): RealmMyTeam?
    suspend fun getTeamLinks(): List<RealmMyTeam>
    suspend fun getTeamById(teamId: String): RealmMyTeam?
    suspend fun getTeamSummaryById(teamId: String): TeamSummary?
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
    suspend fun addResourceLinks(teamId: String, resources: List<RealmMyLibrary>, user: RealmUser?)
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
    suspend fun logTeamVisit(teamId: String, userName: String?, userPlanetCode: String?,
        userParentCode: String?, teamType: String?
    )
    suspend fun createTeamAndAddMember(request: CreateTeamRequest, user: RealmUser): Result<String>
    suspend fun updateTeam(teamId: String, name: String, description: String, services: String,
        rules: String, updatedBy: String?
    ): Result<Boolean>
    suspend fun updateTeamDetails(
        teamId: String, name: String, description: String, services: String, rules: String,
        teamType: String, isPublic: Boolean, createdBy: String
    ): Boolean
    suspend fun syncTeamActivities()
    suspend fun getTeamTransactionsWithBalance(
        teamId: String, startDate: Long? = null,
        endDate: Long? = null, sortAscending: Boolean = false
    ): Flow<List<Transaction>>
    suspend fun createTransaction(
        teamId: String, type: String, note: String, amount: Int, date: Long,
        parentCode: String?, planetCode: String?
    ): Result<Unit>
    suspend fun respondToMemberRequest(teamId: String, userId: String, accept: Boolean): Result<Unit>
    suspend fun getTeamType(teamId: String): String?
    suspend fun getJoinedMembers(teamId: String): List<RealmUser>
    suspend fun getJoinedMembersAndSave(teamId: String): List<RealmUser>
    suspend fun getJoinedMembersWithVisitInfo(teamId: String): List<JoinedMemberData>
    suspend fun getJoinedMemberCount(teamId: String): Int
    suspend fun getAssignee(userId: String): RealmUser?
    suspend fun getRequestedMembers(teamId: String): List<RealmUser>
    suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String? = null): Boolean
    suspend fun createEnterprise(name: String, description: String, services: String,
        rules: String, isPublic: Boolean, user: RealmUser
    ): Result<String>
    suspend fun updateTeamLeader(teamId: String, newLeaderId: String): Boolean
    suspend fun getNextLeaderCandidate(teamId: String, excludeUserId: String?): RealmUser?
    suspend fun getTeamCreator(teamId: String): String?
    suspend fun getAvailableResourcesToAdd(teamId: String): List<RealmMyLibrary>
    suspend fun getTeamVisitCount(userName: String?, teamId: String?): Long

    suspend fun insertTeamLog(json: JsonObject)
    suspend fun insertTeamLogs(logs: List<JsonObject>)
    suspend fun getLastVisit(userName: String?, teamId: String?): Long?
    fun serializeTeamActivities(log: RealmTeamLog, context: Context): JsonObject
    fun insertMyTeam(realm: io.realm.Realm, doc: com.google.gson.JsonObject)
    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
    fun bulkInsertTasksFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
    fun bulkInsertTeamActivitiesFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
}
