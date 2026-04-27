package org.ole.planet.myplanet.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.CreateTeamRequest
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamStatus
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

sealed class TeamActionResult {
    object Success : TeamActionResult()
    data class Failure(val message: String?) : TeamActionResult()
    object NameExists : TeamActionResult()
}

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val _teamData = MutableStateFlow<List<TeamDetails>>(emptyList())
    val teamData: StateFlow<List<TeamDetails>> = _teamData

    private val _taskList = MutableStateFlow<List<RealmTeamTask>>(emptyList())
    val taskList: StateFlow<List<RealmTeamTask>> = _taskList

    fun loadTasks(teamId: String) {
        viewModelScope.launch {
            teamsRepository.getTasksByTeamId(teamId).collectLatest { tasks ->
                _taskList.value = tasks
            }
        }
    }

    private var currentTeamsDetails: List<TeamDetails> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentUserId: String? = null
    private var currentFromDashboard: Boolean = false
    private var currentType: String? = null
    private var loadJob: kotlinx.coroutines.Job? = null


    fun loadTeams(fromDashboard: Boolean, type: String?, userId: String?) {
        currentFromDashboard = fromDashboard
        currentType = type
        currentUserId = userId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when {
                fromDashboard -> {
                    if (userId != null) {
                        teamsRepository.getMyTeamDetailsFlow(userId).collectLatest { list ->
                            applyFilters(list, currentSearchQuery)
                        }
                    }
                }
                type == "enterprise" -> {
                    val teamList = teamsRepository.getShareableEnterpriseDetails(userId)
                    applyFilters(teamList, currentSearchQuery)
                }
                else -> {
                    val teamList = teamsRepository.getTeamDetails(userId)
                    applyFilters(teamList, currentSearchQuery)
                }
            }
        }
    }

    fun searchTeams(query: String) {
        currentSearchQuery = query
        applyFilters(currentTeamsDetails, currentSearchQuery)
    }

    private fun applyFilters(teams: List<TeamDetails>, searchQuery: String) {
        currentTeamsDetails = teams
        val filteredList = if (searchQuery.isEmpty()) {
            teams
        } else {
            teams.filter {
                it.name?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        _teamData.value = filteredList
    }

    fun requestToJoin(teamId: String, userId: String?, userPlanetCode: String?, teamType: String?) {
        val currentList = _teamData.value.toMutableList()
        val index = currentList.indexOfFirst { it._id == teamId }
        if (index != -1) {
            val team = currentList[index]
            val newStatus = TeamStatus(
                isMember = false,
                isLeader = false,
                hasPendingRequest = true
            )
            currentList[index] = team.copy(teamStatus = newStatus)
            _teamData.value = currentList
        }

        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                teamsRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
                teamsRepository.syncTeamActivities()
            }
            loadTeams(currentFromDashboard, currentType, currentUserId)
        }
    }

    fun leaveTeam(teamId: String, userId: String?) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                teamsRepository.leaveTeam(teamId, userId)
                teamsRepository.syncTeamActivities()
            }
            loadTeams(currentFromDashboard, currentType, currentUserId)
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

        val request = CreateTeamRequest(
            name = name,
            description = description,
            services = services,
            rules = rules,
            teamType = teamType,
            isPublic = isPublic,
            category = category
        )

        return teamsRepository.createTeamAndAddMember(request, userModel)
            .fold(
                onSuccess = { TeamActionResult.Success },
                onFailure = { TeamActionResult.Failure(it.message) }
            )
    }
}
