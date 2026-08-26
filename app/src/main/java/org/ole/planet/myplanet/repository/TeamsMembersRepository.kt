package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.JoinedMemberData
import org.ole.planet.myplanet.model.UserEntity

interface TeamsMembersRepository {
    suspend fun isMember(userId: String?, teamId: String): Boolean
    suspend fun isTeamLeader(teamId: String, userId: String?): Boolean
    suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean
    suspend fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?)
    suspend fun leaveTeam(teamId: String, userId: String?)
    suspend fun removeMember(teamId: String, userId: String)
    suspend fun respondToMemberRequest(teamId: String, userId: String, accept: Boolean): Result<Unit>
    suspend fun getJoinedMembers(teamId: String): List<UserEntity>
    suspend fun refreshJoinedMembersForLogin(teamId: String): List<UserEntity>
    suspend fun getJoinedMembersWithVisitInfo(teamId: String): List<JoinedMemberData>
    suspend fun getJoinedMemberCount(teamId: String): Int
    suspend fun getRequestedMembers(teamId: String): List<UserEntity>
    suspend fun updateTeamLeader(teamId: String, newLeaderId: String): Boolean
    suspend fun getNextLeaderCandidate(teamId: String, excludeUserId: String?): UserEntity?

    /**
     * UI-facing alias to record a team activity.
     * The internal sync layer calls syncTeamActivities() directly.
     */
    suspend fun recordTeamActivity()
}
