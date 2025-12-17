package org.ole.planet.myplanet.ui.team

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
import org.ole.planet.myplanet.repository.TeamRepository

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamRepository: TeamRepository
) : ViewModel() {
    private val _teamData = MutableStateFlow<List<TeamData>>(emptyList())
    val teamData: StateFlow<List<TeamData>> = _teamData
    private var currentTeams: List<RealmMyTeam> = emptyList()


    fun prepareTeamData(teams: List<RealmMyTeam>, userId: String?) {
        currentTeams = teams
        viewModelScope.launch {
            val processedTeams = withContext(Dispatchers.Default) {
                val validTeams = teams.filter {
                    !it._id.isNullOrBlank() && (it.status == null || it.status != "archived")
                }

                if (validTeams.isEmpty()) {
                    return@withContext emptyList<TeamData>()
                }

                val teamIds = validTeams.mapNotNull { it._id }

                val visitCountsDeferred = async { teamRepository.getRecentVisitCounts(teamIds) }
                val memberStatusesDeferred = async { teamRepository.getTeamMemberStatuses(userId, teamIds) }

                val visitCounts = visitCountsDeferred.await()
                val memberStatuses = memberStatusesDeferred.await()

                val teamDataList = validTeams.map { team ->
                    val teamId = team._id.orEmpty()
                    val status = memberStatuses[teamId]
                    TeamData(
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
                    compareByDescending<TeamData> {
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
            teamRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
            teamRepository.syncTeamActivities()
            prepareTeamData(currentTeams, userId)
        }
    }

    fun leaveTeam(teamId: String, userId: String?) {
        viewModelScope.launch {
            teamRepository.leaveTeam(teamId, userId)
            teamRepository.syncTeamActivities()
            prepareTeamData(currentTeams, userId)
        }
    }
}
