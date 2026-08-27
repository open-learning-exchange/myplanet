package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.JoinedMemberData
import org.ole.planet.myplanet.model.UserEntity

/**
 * Membership, leadership, and roster capability of the team domain.
 *
 * One of the three sub-interfaces composed into [TeamsRepository] (alongside
 * [TeamsFinancesRepository] and [TeamsNotificationsRepository]). Callers that
 * only need membership checks — a notification worker deciding who to alert, a
 * permission gate in a non-team screen — depend on this narrow contract instead
 * of the full composite, so they are insulated from unrelated finance and
 * notification surface area.
 *
 * ## KMP rationale
 *
 * The split also anticipates a Kotlin Multiplatform (KMP) future: a shared
 * `commonMain` module can declare this interface alone and supply per-platform
 * `expect`/`actual` implementations, while the Android app re-composes it into
 * [TeamsRepository]. Keep new membership concerns here rather than growing the
 * composite directly.
 */
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
