package org.ole.planet.myplanet.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.CreateTeamRequest
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamStatus
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

import org.ole.planet.myplanet.services.UserSessionManager

sealed class TeamActionResult {
    object Success : TeamActionResult()
    data class Failure(val message: String?) : TeamActionResult()
    object NameExists : TeamActionResult()
}

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val userSessionManager: UserSessionManager
) : ViewModel() {
    private val _teamData = MutableStateFlow<List<TeamDetails>>(emptyList())
    val teamData: StateFlow<List<TeamDetails>> = _teamData

    var userId: String? = null
    var isGuestUser: Boolean = false
    var userPlanetCode: String? = null

    private var teamList: List<TeamSummary> = emptyList()
    var conditionApplied: Boolean = false
        private set
    private var currentTeams: List<TeamSummary> = emptyList()

    init {
        viewModelScope.launch {
            val user = userSessionManager.getUserModel()
            userId = user?.id
            isGuestUser = user?.isGuest() ?: false
            userPlanetCode = user?.planetCode
        }
    }

    fun prepareTeamData(teams: List<TeamSummary>, userId: String?) {
        currentTeams = teams
        viewModelScope.launch {
            val processedTeams = withContext(dispatcherProvider.io) {
                val validTeams = teams.filter {
                    !it._id.isBlank() && (it.status == null || it.status != "archived")
                }

                if (validTeams.isEmpty()) {
                    return@withContext emptyList<TeamDetails>()
                }

                val teamIds = validTeams.map { it._id }

                val visitCountsDeferred = async { teamsRepository.getRecentVisitCounts(teamIds) }
                val memberStatusesDeferred = async { teamsRepository.getTeamMemberStatuses(userId, teamIds) }

                val visitCounts = visitCountsDeferred.await()
                val memberStatuses = memberStatusesDeferred.await()

                val teamDataList = validTeams.map { team ->
                    val teamId = team._id
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

    fun reloadTeams(type: String?, fromDashboard: Boolean) {
        teamList = emptyList()
        loadTeams(type, fromDashboard)
    }

    fun loadTeams(type: String?, fromDashboard: Boolean) {
        if (teamList.isNotEmpty()) return

        viewModelScope.launch {
            when {
                fromDashboard -> {
                    userId?.let { uid ->
                        teamsRepository.getMyTeamsFlow(uid).collectLatest { list ->
                            teamList = list.mapNotNull {
                                val id = it._id ?: return@mapNotNull null
                                TeamSummary(
                                    _id = id,
                                    name = it.name ?: "",
                                    teamType = it.teamType,
                                    teamPlanetCode = it.teamPlanetCode,
                                    createdDate = it.createdDate,
                                    type = it.type,
                                    status = it.status,
                                    teamId = it.teamId,
                                    description = it.description,
                                    services = it.services,
                                    rules = it.rules
                                )
                            }
                            prepareTeamData(teamList, userId)
                        }
                    }
                }
                type == "enterprise" -> {
                    conditionApplied = true
                    teamList = teamsRepository.getShareableEnterpriseSummaries()
                    prepareTeamData(teamList, userId)
                }
                else -> {
                    conditionApplied = false
                    teamList = teamsRepository.getTeamSummaries()
                    prepareTeamData(teamList, userId)
                }
            }
        }
    }

    fun onSearchTextChanged(searchText: String) {
        val filteredList = if (searchText.isEmpty()) {
            teamList
        } else {
            teamList.filter {
                it.name.contains(searchText, ignoreCase = true) == true
            }
        }
        prepareTeamData(filteredList, userId)
    }

    suspend fun createTeam(
        name: String,
        description: String,
        services: String,
        rules: String,
        teamType: String,
        isPublic: Boolean,
        category: String?
    ): TeamActionResult {
        val teamTypeForValidation = if (category == "enterprise") "enterprise" else "team"
        if (teamsRepository.isTeamNameExists(name, teamTypeForValidation, null)) {
            return TeamActionResult.NameExists
        }

        val request = CreateTeamRequest(
            name = name,
            description = description,
            services = services,
            rules = rules,
            teamType = teamType,
            isPublic = isPublic,
            category = category
        )

        val userModel = userSessionManager.getUserModel() ?: return TeamActionResult.Failure("User not found")

        return teamsRepository.createTeamAndAddMember(request, userModel)
            .fold(
                onSuccess = { TeamActionResult.Success },
                onFailure = { TeamActionResult.Failure(it.message) }
            )
    }
}
