package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.JoinedMemberData
import org.ole.planet.myplanet.model.UserEntity

/**
 * Membership, leadership, and roster capability of the team domain.
 *
 * One of the three sub-interfaces composed into [TeamsRepository] (alongside
 * [TeamsFinancesRepository] and [TeamsNotificationsRepository]). Taking this
 * narrow contract instead of the full composite keeps a caller's surface area
 * proportional to what it actually calls — for example, `RequestsViewModel`
 * injects only [TeamsMembersRepository] and so never sees finance or
 * notification methods. (Broad team callers such as `TeamViewModel` and
 * `TaskNotificationWorker` take the full composite, since they span more than
 * membership.)
 *
 * ## Kotlin Multiplatform
 *
 * The split is primarily an interface-segregation choice; myPlanet is an
 * Android-only single-module app today. If a Kotlin Multiplatform (KMP) target
 * is ever pursued, this narrow interface would also be the natural seam for it:
 * a shared `commonMain` module could declare it alone with per-platform
 * `expect`/`actual` implementations, while the Android app re-composes it into
 * [TeamsRepository]. That is a conditional benefit, not a committed direction.
 * Prefer adding new membership concerns here rather than growing the composite.
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
