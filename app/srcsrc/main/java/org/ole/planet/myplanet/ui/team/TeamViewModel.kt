package org.ole.planet.myplanet.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import javax.inject.Inject

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val userProfileDbHandler: UserProfileDbHandler
) : ViewModel() {
    private val teamStatusCache = mutableMapOf<String, TeamStatus>()
    private val visitCountsCache = mutableMapOf<String, Long>()
    val currentUser: RealmUserModel? = userProfileDbHandler.getUserModelCopy()

    suspend fun requestToJoinTeam(teamId: String, teamType: String?) {
        teamRepository.requestToJoin(teamId, currentUser?.id, currentUser?.planetCode, teamType)
    }

    suspend fun leaveTeam(teamId: String) {
        teamRepository.leaveTeam(teamId, currentUser?.id)
    }

    suspend fun isTeamNameExists(name: String, type: String, excludeTeamId: String? = null): Boolean {
        return teamRepository.isTeamNameExists(name, type, excludeTeamId)
    }

    suspend fun createTeam(
        category: String?,
        name: String,
        description: String,
        services: String,
        rules: String,
        teamType: String?,
        isPublic: Boolean,
        user: RealmUserModel,
    ): Result<String> {
        return teamRepository.createTeam(category, name, description, services, rules, teamType, isPublic, user)
    }

    suspend fun updateTeam(
        teamId: String,
        name: String,
        description: String,
        services: String,
        rules: String,
        updatedBy: String?,
    ): Result<Boolean> {
        return teamRepository.updateTeam(teamId, name, description, services, rules, updatedBy)
    }

    fun getMyTeamsFlow(userId: String) = teamRepository.getMyTeamsFlow(userId)

    suspend fun getShareableEnterprises(): List<RealmMyTeam> {
        return teamRepository.getShareableEnterprises()
    }

    suspend fun getShareableTeams(): List<RealmMyTeam> {
        return teamRepository.getShareableTeams()
    }

    suspend fun updateList(
        list: List<RealmMyTeam>
    ): Pair<List<TeamData>, Map<String, Long>>? {
        val userId = currentUser?.id
        val validTeams = list.filter {
            !it._id.isNullOrBlank() && (it.status == null || it.status != "archived")
        }

        if (validTeams.isEmpty()) {
            return null
        }

        val teamIds = validTeams.mapNotNull { it._id?.takeIf { id -> id.isNotBlank() } }
        val (cachedVisitIds, nonCachedVisitIds) = teamIds.partition { it in visitCountsCache }

        val visitCountsDeferred = viewModelScope.async(Dispatchers.IO) {
            if (nonCachedVisitIds.isNotEmpty()) {
                teamRepository.getRecentVisitCounts(nonCachedVisitIds)
            } else {
                emptyMap()
            }
        }

        val statusResults = mutableMapOf<String, TeamStatus>()
        val idsToFetch = linkedSetOf<String>()
        validTeams.forEach { team ->
            val teamId = team._id.orEmpty()
            if (teamId.isBlank()) return@forEach
            val cacheKey = "${teamId}_${userId}"
            val cachedStatus = teamStatusCache[cacheKey]
            if (cachedStatus != null) {
                statusResults[teamId] = cachedStatus
            } else {
                idsToFetch += teamId
            }
        }

        if (idsToFetch.isNotEmpty()) {
            val batchStatuses = teamRepository.getTeamMemberStatuses(userId, idsToFetch)
            batchStatuses.forEach { (teamId, memberStatus) ->
                val status = TeamStatus(
                    isMember = memberStatus.isMember,
                    isLeader = memberStatus.isLeader,
                    hasPendingRequest = memberStatus.hasPendingRequest
                )
                val cacheKey = "${teamId}_${userId}"
                teamStatusCache[cacheKey] = status
                statusResults[teamId] = status
            }
        }

        val newVisitCounts = visitCountsDeferred.await()
        newVisitCounts.forEach { (id, count) -> visitCountsCache[id] = count }
        val calculatedVisitCounts =
            cachedVisitIds.associateWith { visitCountsCache[it]!! } + newVisitCounts

        val sortedTeams = validTeams.sortedWith(
            compareByDescending<RealmMyTeam> { team ->
                val teamId = team._id.orEmpty()
                val status = statusResults[teamId] ?: TeamStatus(false, false, false)
                when {
                    status.isLeader -> 3
                    status.isMember -> 2
                    else -> 1
                }
            }.thenByDescending { team ->
                calculatedVisitCounts[team._id.orEmpty()] ?: 0L
            }
        )

        val newList = sortedTeams.map { team ->
            val teamId = team._id.orEmpty()
            val cacheKey = "${teamId}_${userId}"
            TeamData(
                _id = team._id,
                name = team.name,
                teamType = team.teamType,
                createdDate = team.createdDate,
                type = team.type,
                status = team.status,
                visitCount = calculatedVisitCounts[teamId] ?: 0L,
                teamStatus = teamStatusCache[cacheKey],
                description = team.description,
                services = team.services,
                rules = team.rules,
                teamId = team.teamId
            )
        }
        return Pair(newList, calculatedVisitCounts)
    }

    fun syncTeamActivities() {
        viewModelScope.launch {
            teamRepository.syncTeamActivities()
        }
    }
}
