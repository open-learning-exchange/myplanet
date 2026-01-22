package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserSessionManager
import org.ole.planet.myplanet.utils.AndroidDecrypter

class TeamMemberRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val userSessionManager: UserSessionManager,
    @AppPreferences private val preferences: SharedPreferences,
) : RealmRepository(databaseService), TeamMemberRepository {

    override suspend fun isMember(userId: String?, teamId: String): Boolean {
        userId ?: return false
        return count(RealmMyTeam::class.java) {
            equalTo("userId", userId)
            equalTo("teamId", teamId)
            equalTo("docType", "membership")
        } > 0
    }

    override suspend fun isTeamLeader(teamId: String, userId: String?): Boolean {
        if (teamId.isBlank() || userId.isNullOrBlank()) return false
        return count(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
            equalTo("docType", "membership")
            equalTo("userId", userId)
            equalTo("isLeader", true)
        } > 0
    }

    override suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean {
        if (teamId.isBlank() || userId.isNullOrBlank()) return false
        return count(RealmMyTeam::class.java) {
            equalTo("docType", "request")
            equalTo("teamId", teamId)
            equalTo("userId", userId)
        } > 0
    }

    override suspend fun getTeamMemberStatuses(userId: String?, teamIds: Collection<String>): Map<String, TeamMemberStatus> {
        if (userId.isNullOrBlank() || teamIds.isEmpty()) return emptyMap()

        val validIds = teamIds.filter { it.isNotBlank() }.distinct()
        if (validIds.isEmpty()) return emptyMap()

        val memberships = queryList(RealmMyTeam::class.java) {
            equalTo("userId", userId)
            equalTo("docType", "membership")
            `in`("teamId", validIds.toTypedArray())
        }

        val pendingRequests = queryList(RealmMyTeam::class.java) {
            equalTo("userId", userId)
            equalTo("docType", "request")
            `in`("teamId", validIds.toTypedArray())
        }

        val membershipMap = memberships
            .mapNotNull { it.teamId }
            .toSet()

        val leaderMap = memberships
            .filter { it.isLeader }
            .mapNotNull { it.teamId }
            .toSet()

        val pendingRequestMap = pendingRequests
            .mapNotNull { it.teamId }
            .toSet()

        return validIds.associateWith { teamId ->
            TeamMemberStatus(
                isMember = teamId in membershipMap,
                isLeader = teamId in leaderMap,
                hasPendingRequest = teamId in pendingRequestMap
            )
        }
    }

    override suspend fun getRecentVisitCounts(teamIds: Collection<String>): Map<String, Long> {
        if (teamIds.isEmpty()) return emptyMap()

        val validIds = teamIds.filter { it.isNotBlank() }.distinct()
        if (validIds.isEmpty()) return emptyMap()

        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis

        val recentLogs = queryList(RealmTeamLog::class.java) {
            equalTo("type", "teamVisit")
            greaterThan("time", cutoff)
            `in`("teamId", validIds.toTypedArray())
        }

        return recentLogs
            .mapNotNull { it.teamId }
            .groupingBy { it }
            .eachCount()
            .mapValues { it.value.toLong() }
    }

    override suspend fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?) {
        if (teamId.isBlank() || userId.isNullOrBlank()) return
        executeTransaction { realm ->
            val request = realm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
            request.docType = "request"
            request.createdDate = Date().time
            request.teamType = teamType
            request.userId = userId
            request.teamId = teamId
            request.updated = true
            request.teamPlanetCode = userPlanetCode
            request.userPlanetCode = userPlanetCode
        }
    }

    override suspend fun respondToMemberRequest(
        teamId: String,
        userId: String,
        accept: Boolean,
    ): Result<Unit> {
        if (teamId.isBlank() || userId.isBlank()) {
            return Result.failure(IllegalArgumentException("teamId and userId cannot be blank"))
        }

        return runCatching {
            executeTransaction { realm ->
                val request = realm.where(RealmMyTeam::class.java)
                    .equalTo("teamId", teamId)
                    .equalTo("userId", userId)
                    .equalTo("docType", "request")
                    .findFirst()
                    ?: throw IllegalStateException("Request not found for user $userId")

                if (accept) {
                    request.docType = "membership"
                    request.updated = true
                } else {
                    request.deleteFromRealm()
                }
            }
        }
    }

    override suspend fun leaveTeam(teamId: String, userId: String?) {
        if (teamId.isBlank() || userId.isNullOrBlank()) return
        executeTransaction { realm ->
            val memberships = realm.where(RealmMyTeam::class.java)
                .equalTo("userId", userId)
                .equalTo("teamId", teamId)
                .equalTo("docType", "membership")
                .findAll()
            memberships.forEach { member ->
                member?.deleteFromRealm()
            }
        }
    }

    override suspend fun removeMember(teamId: String, userId: String) {
        if (teamId.isBlank() || userId.isBlank()) return
        executeTransaction { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userId)
                .equalTo("docType", "membership")
                .findAll()
                .deleteAllFromRealm()
        }
    }

    override suspend fun addResourceLinks(
        teamId: String,
        resources: List<RealmMyLibrary>,
        user: RealmUserModel?,
    ) {
        if (teamId.isBlank() || resources.isEmpty() || user == null) return
        executeTransaction { realm ->
            resources.forEach { resource ->
                val teamResource = realm.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
                teamResource.teamId = teamId
                teamResource.title = resource.title
                teamResource.status = user.parentCode
                teamResource.resourceId = resource._id
                teamResource.docType = "resourceLink"
                teamResource.updated = true
                teamResource.teamType = "local"
                teamResource.teamPlanetCode = user.planetCode
                teamResource.userPlanetCode = user.planetCode
            }
        }
    }

    override suspend fun removeResourceLink(teamId: String, resourceId: String) {
        if (teamId.isBlank() || resourceId.isBlank()) return
        executeTransaction { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("resourceId", resourceId)
                .equalTo("docType", "resourceLink")
                .findFirst()
                ?.let { teamResource ->
                    teamResource.resourceId = ""
                    teamResource.updated = true
                }
        }
    }

    override suspend fun getJoinedMembers(teamId: String): List<RealmUserModel> {
        val teamMembers = queryList(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
            equalTo("docType", "membership")
        }.mapNotNull { it.userId }

        return queryList(RealmUserModel::class.java) {
            `in`("id", teamMembers.toTypedArray())
        }
    }

    override suspend fun getJoinedMembersWithVisitInfo(teamId: String): List<JoinedMemberData> {
        data class MemberStats(
            val member: RealmUserModel,
            val visitCount: Long,
            val lastVisitTimestamp: Long?,
            val isLeader: Boolean
        )

        val membersStats = withRealm { realm ->
            val members = RealmMyTeam.getJoinedMember(teamId, realm).map { realm.copyFromRealm(it) }.toMutableList()
            val communityLeadersJson = preferences.getString("communityLeaders", "") ?: ""

            if (communityLeadersJson.isNotEmpty()) {
                val adminUsers = RealmUserModel.parseLeadersJson(communityLeadersJson)

                val teamUserIds = realm.where(RealmMyTeam::class.java)
                    .equalTo("teamId", teamId)
                    .findAll()
                    .mapNotNull { it.userId }
                    .toSet()

                for (admin in adminUsers) {
                    val adminFullId = "org.couchdb.user:${admin.name}"

                    if (adminFullId in teamUserIds && !members.any { it.name == admin.name }) {
                        val adminFromRealm = realm.where(RealmUserModel::class.java)
                            .equalTo("name", admin.name)
                            .findFirst()
                        if (adminFromRealm != null) {
                            members.add(realm.copyFromRealm(adminFromRealm))
                        } else {
                            members.add(admin)
                        }
                    }
                }
            }

            val leaderRecords = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("isLeader", true)
                .findAll()


            val leaderIds = leaderRecords.mapNotNull { it.userId }.toSet()
            val leaders = mutableListOf<RealmUserModel>()
            val nonLeaders = mutableListOf<RealmUserModel>()

            members.forEach { member ->
                if (member.id in leaderIds) {
                    leaders.add(member)
                } else {
                    nonLeaders.add(member)
                }
            }

            val orderedMembers = leaders + nonLeaders
            orderedMembers.map { member ->
                val lastVisitTimestamp = RealmTeamLog.getLastVisit(realm, member.name, teamId)
                val visitCount = RealmTeamLog.getVisitCount(realm, member.name, teamId)
                val isLeader = member.id in leaderIds
                MemberStats(member, visitCount, lastVisitTimestamp, isLeader)
            }
        }

        return membersStats.map { stats ->
            val profileLastVisit = userSessionManager.getLastVisit(stats.member)
            val offlineVisits = "${userSessionManager.getOfflineVisits(stats.member)}"
            JoinedMemberData(
                stats.member,
                stats.visitCount,
                stats.lastVisitTimestamp,
                offlineVisits,
                profileLastVisit,
                stats.isLeader
            )
        }
    }

    override suspend fun getJoinedMemberCount(teamId: String): Int {
        return count(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
            equalTo("docType", "membership")
        }.toInt()
    }

    override suspend fun getRequestedMembers(teamId: String): List<RealmUserModel> {
        val teamMembers = queryList(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
            equalTo("docType", "request")
        }.mapNotNull { it.userId }

        return queryList(RealmUserModel::class.java) {
            `in`("id", teamMembers.toTypedArray())
        }
    }

    override suspend fun updateTeamLeader(teamId: String, newLeaderId: String): Boolean {
        var success = false
        executeTransaction { realm ->
            val currentLeaders = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("docType", "membership")
                .equalTo("isLeader", true)
                .findAll()
            currentLeaders.forEach { it.isLeader = false }

            val newLeader = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("docType", "membership")
                .equalTo("userId", newLeaderId)
                .findFirst()

            if (newLeader != null) {
                newLeader.isLeader = true
                success = true
            }
        }
        return success
    }

    override suspend fun getNextLeaderCandidate(teamId: String, excludeUserId: String?): RealmUserModel? {
        return withRealm { realm ->
            val query = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("docType", "membership")
                .equalTo("isLeader", false)
                .notEqualTo("status", "archived")

            excludeUserId?.let {
                query.notEqualTo("userId", it)
            }

            val members = query.findAll()

            if (members.isEmpty()) {
                return@withRealm null
            }

            val userIds = members.mapNotNull { it.userId }.toTypedArray()
            if (userIds.isEmpty()) {
                return@withRealm null
            }

            val users = realm.where(RealmUserModel::class.java)
                .`in`("id", userIds)
                .findAll()

            val userMap = users.associateBy { it.id }
            val successorMember = members.maxByOrNull { member ->
                userMap[member.userId]?.let { user ->
                    RealmTeamLog.getVisitCount(realm, user.name, teamId)
                } ?: 0L
            }

            successorMember?.userId?.let { id ->
                userMap[id]?.let {
                    realm.copyFromRealm(it)
                }
            }
        }
    }
}
