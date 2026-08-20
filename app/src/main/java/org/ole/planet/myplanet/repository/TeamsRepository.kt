package org.ole.planet.myplanet.repository

import android.net.Uri
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.CreateTeamRequest
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamResourceDto
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserEntity

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
    val serialized: JsonObject,
    val isDeletePending: Boolean = false,
    val imageName: String? = null
)

interface TeamsRepository : TeamsFinancesRepository, TeamsMembersRepository, TeamsNotificationsRepository {
    suspend fun getAllActiveTeams(): List<MyTeam>
    suspend fun getMyTeamsFlow(userId: String): Flow<List<MyTeam>>
    suspend fun getTeamSummaries(userId: String?): List<TeamSummary>
    suspend fun getShareableEnterpriseSummaries(userId: String?): List<TeamSummary>
    fun getMyTeamDetailsFlow(userId: String, type: String? = null): Flow<List<TeamDetails>>
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
    fun getTasksFlow(userId: String?): Flow<List<TeamTask>>
    suspend fun addResourceLinks(teamId: String, resources: List<TeamResourceDto>, userId: String?)
    suspend fun removeResourceLink(teamId: String, resourceId: String)
    suspend fun createLocalResourceLink(teamId: String, resourceId: String, title: String?, planetCode: String?)
    suspend fun deleteTask(taskId: String)
    suspend fun createTask(title: String, description: String, deadline: Long, teamId: String, assigneeId: String?)
    suspend fun updateTask(taskId: String, title: String, description: String, deadline: Long, assigneeId: String?)
    suspend fun assignTask(taskId: String, assigneeId: String?)
    suspend fun setTaskCompletion(taskId: String, completed: Boolean)
    suspend fun getPendingTasksForUser(userId: String, start: Long, end: Long): List<TeamTask>
    suspend fun markTasksNotified(taskIds: Collection<String>)
    suspend fun getTasksByTeamId(teamId: String): Flow<List<TeamTask>>
    suspend fun logTeamVisit(teamId: String, userName: String?, userPlanetCode: String?,
        userParentCode: String?, teamType: String?
    )
    suspend fun createTeamAndAddMember(request: CreateTeamRequest, user: UserEntity): Result<String>
    suspend fun updateTeam(teamId: String, name: String, description: String, services: String,
        rules: String, updatedBy: String?, profileImage: String? = null
    ): Result<Boolean>
    suspend fun uploadTeamImage(uri: Uri): String
    suspend fun updateTeamDetails(
        teamId: String, name: String, description: String, services: String, rules: String,
        teamType: String, isPublic: Boolean, createdBy: String, profileImage: String? = null
    ): Boolean
    suspend fun getTeamType(teamId: String): String?
    suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String? = null): Boolean
    suspend fun getTeamCreator(teamId: String): String?
    suspend fun getAvailableResourcesToAdd(teamId: String): List<MyLibrary>
    suspend fun getLastVisit(userName: String?, teamId: String?): Long?
    fun getTeamNameFromPrefs(): String?
}
