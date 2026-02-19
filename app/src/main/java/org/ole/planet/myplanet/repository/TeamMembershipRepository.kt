package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser

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

interface TeamMembershipRepository {
    suspend fun getAllActiveTeams(): List<RealmMyTeam>
    suspend fun getMyTeamsFlow(userId: String): Flow<List<RealmMyTeam>>
    suspend fun getShareableTeams(): List<RealmMyTeam>
    suspend fun getShareableEnterprises(): List<RealmMyTeam>
    suspend fun getTeamByDocumentIdOrTeamId(id: String): RealmMyTeam?
    suspend fun getTeamByIdOrTeamId(id: String): RealmMyTeam?
    suspend fun getTeamLinks(): List<RealmMyTeam>
    suspend fun getTeamById(teamId: String): RealmMyTeam?
    suspend fun getJoinRequestTeamId(requestId: String): String?
    suspend fun getJoinRequestNotifications(userId: String?): List<JoinRequestNotification>
    suspend fun isMember(userId: String?, teamId: String): Boolean
    suspend fun isTeamLeader(teamId: String, userId: String?): Boolean
    suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean
    suspend fun getTeamMemberStatuses(userId: String?, teamIds: Collection<String>): Map<String, TeamMemberStatus>
    suspend fun getRecentVisitCounts(teamIds: Collection<String>): Map<String, Long>
    suspend fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?)
    suspend fun leaveTeam(teamId: String, userId: String?)
    suspend fun removeMember(teamId: String, userId: String)
    suspend fun logTeamVisit(
        teamId: String,
        userName: String?,
        userPlanetCode: String?,
        userParentCode: String?,
        teamType: String?,
    )

    suspend fun createTeamAndAddMember(teamObject: JsonObject, user: RealmUser): Result<String>
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
    suspend fun respondToMemberRequest(teamId: String, userId: String, accept: Boolean): Result<Unit>
    suspend fun getTeamType(teamId: String): String?
    suspend fun getJoinedMembers(teamId: String): List<RealmUser>
    suspend fun getJoinedMembersWithVisitInfo(teamId: String): List<JoinedMemberData>
    suspend fun getJoinedMemberCount(teamId: String): Int
    suspend fun getRequestedMembers(teamId: String): List<RealmUser>
    suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String? = null): Boolean
    suspend fun createEnterprise(
        name: String,
        description: String,
        services: String,
        rules: String,
        isPublic: Boolean,
        user: RealmUser,
    ): Result<String>

    suspend fun updateTeamLeader(teamId: String, newLeaderId: String): Boolean
    suspend fun getNextLeaderCandidate(teamId: String, excludeUserId: String?): RealmUser?
}
