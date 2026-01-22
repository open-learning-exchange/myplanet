package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel

interface TeamMemberRepository {
    suspend fun isMember(userId: String?, teamId: String): Boolean
    suspend fun isTeamLeader(teamId: String, userId: String?): Boolean
    suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean
    suspend fun getTeamMemberStatuses(userId: String?, teamIds: Collection<String>): Map<String, TeamMemberStatus>
    suspend fun getRecentVisitCounts(teamIds: Collection<String>): Map<String, Long>
    suspend fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?)
    suspend fun respondToMemberRequest(teamId: String, userId: String, accept: Boolean): Result<Unit>
    suspend fun leaveTeam(teamId: String, userId: String?)
    suspend fun removeMember(teamId: String, userId: String)
    suspend fun addResourceLinks(teamId: String, resources: List<RealmMyLibrary>, user: RealmUserModel?)
    suspend fun removeResourceLink(teamId: String, resourceId: String)
    suspend fun getJoinedMembers(teamId: String): List<RealmUserModel>
    suspend fun getJoinedMembersWithVisitInfo(teamId: String): List<JoinedMemberData>
    suspend fun getJoinedMemberCount(teamId: String): Int
    suspend fun getRequestedMembers(teamId: String): List<RealmUserModel>
    suspend fun updateTeamLeader(teamId: String, newLeaderId: String): Boolean
    suspend fun getNextLeaderCandidate(teamId: String, excludeUserId: String?): RealmUserModel?
}
