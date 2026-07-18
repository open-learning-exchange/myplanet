package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.CreateTeamRequest
import org.ole.planet.myplanet.model.FinanceReportParams
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamResourceDto
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.model.Transaction

data class VoicePostingPolicy(
    val teamId: String,
    val isPublic: Boolean
) {
    fun canPost(isGuest: Boolean, isMember: Boolean): Boolean {
        return !isGuest && (isMember || isPublic)
    }
}

fun MyTeam.toVoicePostingPolicy(): VoicePostingPolicy {
    return VoicePostingPolicy(
        teamId = this._id ?: this.teamId ?: "",
        isPublic = this.isPublic
    )
}

data class JoinedMemberData(
    val user: UserEntity,
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

data class TeamLabelInfo(
    val teamId: String,
    val name: String,
    val type: String
)

data class JoinRequestInfo(
    val id: String,
    val teamId: String,
    val userId: String
)

data class JoinRequestNotification(
    val requesterName: String,
    val teamName: String,
    val requestId: String
)

data class TeamUploadData(
    val teamId: String?,
    val serialized: JsonObject,
    val isDeletePending: Boolean = false,
    val imageName: String? = null
)

interface TeamsRepository {
    suspend fun getAllActiveTeams(): List<MyTeam>
    suspend fun getMyTeamsFlow(userId: String): Flow<List<MyTeam>>
    suspend fun getResourceIds(teamId: String): List<String>
    suspend fun getTeamSummaries(userId: String?): List<TeamSummary>
    suspend fun getShareableEnterpriseSummaries(userId: String?): List<TeamSummary>
    fun getMyTeamDetailsFlow(userId: String): Flow<List<TeamDetails>>
    suspend fun getShareableEnterpriseDetails(userId: String?): List<TeamDetails>
    suspend fun getTeamDetails(userId: String?): List<TeamDetails>

    suspend fun getTeamResources(teamId: String): List<MyLibrary>
    suspend fun getTeamCourseIds(teamId: String): List<String>
    suspend fun addCoursesToTeam(teamId: String, courseIds: List<String>): Result<Unit>
    suspend fun removeCourseFromTeam(teamId: String, courseId: String): Result<Unit>
    suspend fun getTeamByIdOrTeamId(id: String): MyTeam?
    suspend fun getTeamLinks(): List<MyTeam>
    suspend fun getTeamById(teamId: String): MyTeam?
    suspend fun getTeamSummaryById(teamId: String): TeamSummary?
    suspend fun getTaskTeamInfo(taskId: String): Triple<String, String, String>?
    suspend fun getJoinRequestTeamId(requestId: String): String?
    suspend fun getTeamLabelInfo(teamId: String): TeamLabelInfo?
    suspend fun getJoinRequestInfo(requestId: String?): JoinRequestInfo?
    suspend fun getJoinRequestsInfo(requestIds: List<String>): List<JoinRequestInfo>

    suspend fun getTeamNamesByIds(ids: List<String>): Map<String, String>
    fun getTasksFlow(userId: String?): Flow<List<TeamTask>>
    suspend fun isMember(userId: String?, teamId: String): Boolean
    suspend fun isTeamLeader(teamId: String, userId: String?): Boolean
    suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean
    suspend fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?)
    suspend fun leaveTeam(teamId: String, userId: String?)
    suspend fun removeMember(teamId: String, userId: String)
    suspend fun addResourceLinks(teamId: String, resources: List<TeamResourceDto>, userId: String?)
    suspend fun removeResourceLink(teamId: String, resourceId: String)
    suspend fun deleteTask(taskId: String)
    suspend fun createTask(title: String, description: String, deadline: Long, teamId: String, assigneeId: String?)
    suspend fun updateTask(taskId: String, title: String, description: String, deadline: Long, assigneeId: String?)
    suspend fun assignTask(taskId: String, assigneeId: String?)
    suspend fun setTaskCompletion(taskId: String, completed: Boolean)
    suspend fun getPendingTasksForUser(userId: String, start: Long, end: Long): List<TeamTask>
    suspend fun markTasksNotified(taskIds: Collection<String>)
    suspend fun getTasksByTeamId(teamId: String): Flow<List<TeamTask>>
    suspend fun getReportsFlow(teamId: String): Flow<List<MyTeam>>
    suspend fun exportReportsAsCsv(reports: List<MyTeam>, teamName: String): String
    suspend fun addReport(report: FinanceReportParams)
    suspend fun attachTeamImage(teamId: String, imageName: String, imageData: ByteArray)
    suspend fun updateReport(reportId: String, payload: FinanceReportParams)
    suspend fun archiveReport(reportId: String)
    suspend fun logTeamVisit(teamId: String, userName: String?, userPlanetCode: String?,
        userParentCode: String?, teamType: String?
    )
    suspend fun createTeamAndAddMember(request: CreateTeamRequest, user: UserEntity): Result<String>
    suspend fun updateTeam(teamId: String, name: String, description: String, services: String,
        rules: String, updatedBy: String?
    ): Result<Boolean>
    suspend fun updateTeamDetails(
        teamId: String, name: String, description: String, services: String, rules: String,
        teamType: String, isPublic: Boolean, createdBy: String
    ): Boolean
    suspend fun getTeamTransactionsWithBalance(
        teamId: String, startDate: Long? = null,
        endDate: Long? = null, sortAscending: Boolean = false
    ): Flow<List<Transaction>>
    suspend fun createTransaction(
        teamId: String, type: String, note: String, amount: Int, date: Long,
        parentCode: String?, planetCode: String?,
        imageName: String? = null, imageData: ByteArray? = null
    ): Result<Unit>
    suspend fun respondToMemberRequest(teamId: String, userId: String, accept: Boolean): Result<Unit>
    suspend fun getTeamType(teamId: String): String?
    suspend fun getJoinedMembers(teamId: String): List<UserEntity>
    suspend fun refreshJoinedMembersForLogin(teamId: String): List<UserEntity>
    suspend fun getJoinedMembersWithVisitInfo(teamId: String): List<JoinedMemberData>
    suspend fun getJoinedMemberCount(teamId: String): Int
    suspend fun getAssignee(userId: String): UserEntity?
    suspend fun getRequestedMembers(teamId: String): List<UserEntity>
    suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String? = null): Boolean
    suspend fun updateTeamLeader(teamId: String, newLeaderId: String): Boolean
    suspend fun getNextLeaderCandidate(teamId: String, excludeUserId: String?): UserEntity?
    suspend fun getTeamCreator(teamId: String): String?
    suspend fun getAvailableResourcesToAdd(teamId: String): List<MyLibrary>

    suspend fun getLastVisit(userName: String?, teamId: String?): Long?
}
