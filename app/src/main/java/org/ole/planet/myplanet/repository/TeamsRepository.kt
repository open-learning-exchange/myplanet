package org.ole.planet.myplanet.repository

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

/**
 * The composite team data contract.
 *
 * This interface is the union of three narrowly-scoped sub-interfaces, each owned
 * by a single responsibility:
 *  * [TeamsFinancesRepository] — team transactions and balances
 *  * [TeamsMembersRepository] — join/leave, leadership, and roster queries
 *  * [TeamsNotificationsRepository] — label and join-request lookups for notifications
 *
 * ## Why the split?
 *
 * The sub-interfaces exist so that each consuming layer can depend on only the
 * capability it needs — a notification worker has no compile-time dependency on
 * finance methods, and a finance screen never learns about membership mutation.
 * This mirrors the interface-segregation principle and keeps the consumer's
 * surface area proportional to what it actually calls.
 *
 * ## KMP rationale
 *
 * Although myPlanet ships as an Android-only app today, the team domain is the
 * part of the model most likely to be shared with a future Kotlin Multiplatform
 * (KMP) target (a JVM/JS dashboard or a shared ViewModel layer). The split lets
 * the shared `commonMain` module declare only the sub-interfaces it needs and
 * lets each platform provide its own `expect`/`actual` implementation, while the
 * Android app composes them back together here. Keep new team methods on the
 * narrow sub-interface that owns that concern; only add directly here when a
 * capability spans more than one sub-concern.
 */
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
        rules: String, updatedBy: String?
    ): Result<Boolean>
    suspend fun updateTeamDetails(
        teamId: String, name: String, description: String, services: String, rules: String,
        teamType: String, isPublic: Boolean, createdBy: String
    ): Boolean
    suspend fun getTeamType(teamId: String): String?
    suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String? = null): Boolean
    suspend fun getTeamCreator(teamId: String): String?
    suspend fun getAvailableResourcesToAdd(teamId: String): List<MyLibrary>

    suspend fun getLastVisit(userName: String?, teamId: String?): Long?
    fun getTeamNameFromPrefs(): String?
}
