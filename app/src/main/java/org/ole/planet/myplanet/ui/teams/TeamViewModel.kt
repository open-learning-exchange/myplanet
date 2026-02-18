package org.ole.planet.myplanet.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamStatus
import org.ole.planet.myplanet.repository.TeamsRepository

sealed class TeamActionResult {
    object Success : TeamActionResult()
    data class Failure(val message: String?) : TeamActionResult()
    object NameExists : TeamActionResult()
}

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository
) : ViewModel() {
    private val _teamData = MutableStateFlow<List<TeamDetails>>(emptyList())
    val teamData: StateFlow<List<TeamDetails>> = _teamData
    private var currentTeams: List<RealmMyTeam> = emptyList()


    fun prepareTeamData(teams: List<RealmMyTeam>, userId: String?) {
        currentTeams = teams
        viewModelScope.launch {
            val processedTeams = withContext(Dispatchers.Default) {
                val validTeams = teams.filter {
                    !it._id.isNullOrBlank() && (it.status == null || it.status != "archived")
                }

                if (validTeams.isEmpty()) {
                    return@withContext emptyList<TeamDetails>()
                }

                val teamIds = validTeams.mapNotNull { it._id }

                val visitCountsDeferred = async { teamsRepository.getRecentVisitCounts(teamIds) }
                val memberStatusesDeferred = async { teamsRepository.getTeamMemberStatuses(userId, teamIds) }

                val visitCounts = visitCountsDeferred.await()
                val memberStatuses = memberStatusesDeferred.await()

                val teamDataList = validTeams.map { team ->
                    val teamId = team._id.orEmpty()
                    val status = memberStatuses[teamId]
                    TeamDetails(
                        _id = team._id,
                        name = team.name,
                        teamType = team.teamType,
                        createdDate = team.createdDate,
                        type = team.type,
                        status = team.status,
                        visitCount = visitCounts[teamId] ?: 0L,
                        teamStatus = status?.let {
                            TeamStatus(
                                isMember = it.isMember,
                                isLeader = it.isLeader,
                                hasPendingRequest = it.hasPendingRequest
                            )
                        },
                        description = team.description,
                        services = team.services,
                        rules = team.rules,
                        teamId = team.teamId
                    )
                }

                teamDataList.sortedWith(
                    compareByDescending<TeamDetails> {
                        when {
                            it.teamStatus?.isLeader == true -> 3
                            it.teamStatus?.isMember == true -> 2
                            else -> 1
                        }
                    }.thenByDescending { it.visitCount }
                )
            }
            _teamData.value = processedTeams
        }
    }

    fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?) {
        viewModelScope.launch {
            teamsRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
            teamsRepository.syncTeamActivities()
            prepareTeamData(currentTeams, userId)
        }
    }

    fun leaveTeam(teamId: String, userId: String?) {
        viewModelScope.launch {
            teamsRepository.leaveTeam(teamId, userId)
            teamsRepository.syncTeamActivities()
            prepareTeamData(currentTeams, userId)
        }
    }

    suspend fun createTeam(
        name: String,
        description: String,
        services: String,
        rules: String,
        teamType: String,
        isPublic: Boolean,
        category: String?,
        userModel: RealmUser
    ): TeamActionResult {
        val teamTypeForValidation = if (category == "enterprise") "enterprise" else "team"
        if (teamsRepository.isTeamNameExists(name, teamTypeForValidation, null)) {
            return TeamActionResult.NameExists
        }

        val teamObject = com.google.gson.JsonObject().apply {
            addProperty("name", name)
            addProperty("description", description)
            addProperty("services", services)
            addProperty("rules", rules)
            addProperty("teamType", teamType)
            addProperty("isPublic", isPublic)
            addProperty("category", category)
        }

        return teamsRepository.createTeamAndAddMember(teamObject, userModel)
            .fold(
                onSuccess = { TeamActionResult.Success },
                onFailure = { TeamActionResult.Failure(it.message) }
            )
    }
}
