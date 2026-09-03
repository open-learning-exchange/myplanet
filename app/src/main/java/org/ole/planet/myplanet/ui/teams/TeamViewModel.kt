package org.ole.planet.myplanet.ui.teams

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.CreateTeamRequest
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamStatus
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.TeamUpdateRequest
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider

sealed class TeamActionResult {
    object Success : TeamActionResult()
    data class Failure(val message: String?) : TeamActionResult()
    object NameExists : TeamActionResult()
}

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val realtimeSyncManager: RealtimeSyncManager
) : ViewModel() {
    private val _teamData = MutableStateFlow<List<TeamDetails>>(emptyList())
    val teamData: StateFlow<List<TeamDetails>> = _teamData

    private val _taskList = MutableStateFlow<List<TeamTask>>(emptyList())
    val taskList: StateFlow<List<TeamTask>> = _taskList

    fun getTeamUpdateFlow() = realtimeSyncManager.updatesFor("teams")

    fun loadTasks(teamId: String) {
        loadTaskJob?.cancel()
        loadTaskJob = viewModelScope.launch {
            teamsRepository.getTasksByTeamId(teamId)
                .flowOn(dispatcherProvider.io)
                .collectLatest { tasks ->
                _taskList.value = tasks
            }
        }
    }

    private var currentTeamsDetails: List<TeamDetails> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentUserId: String? = null
    private var currentFromDashboard: Boolean = false
    private var currentType: String? = null
    private var loadJob: Job? = null
    private var loadTaskJob: Job? = null


    fun loadTeams(fromDashboard: Boolean, type: String?, userId: String?) {
        currentFromDashboard = fromDashboard
        currentType = type
        currentUserId = userId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val targetType = type ?: "team"
            when {
                fromDashboard -> {
                    if (userId != null) {
                        teamsRepository.getMyTeamDetailsFlow(userId, targetType)
                            .flowOn(dispatcherProvider.io)
                            .collectLatest { list ->
                                applyFilters(list, currentSearchQuery)
                            }
                    } else {
                        val teamList = withContext(dispatcherProvider.io) {
                            if (targetType == "enterprise") {
                                teamsRepository.getShareableEnterpriseDetails(null)
                            } else {
                                teamsRepository.getTeamDetails(null)
                            }
                        }
                        applyFilters(teamList, currentSearchQuery)
                    }
                }
                targetType == "enterprise" -> {
                    val teamList = withContext(dispatcherProvider.io) {
                        teamsRepository.getShareableEnterpriseDetails(userId)
                    }
                    applyFilters(teamList, currentSearchQuery)
                }
                else -> {
                    val teamList = withContext(dispatcherProvider.io) {
                        teamsRepository.getTeamDetails(userId)
                    }
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
            teamsRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
            teamsRepository.recordTeamActivity()
            loadTeams(currentFromDashboard, currentType, currentUserId)
        }
    }

    fun leaveTeam(teamId: String, userId: String?) {
        viewModelScope.launch {
            teamsRepository.leaveTeam(teamId, userId)
            loadTeams(currentFromDashboard, currentType, currentUserId)
        }
        viewModelScope.launch {
            teamsRepository.recordTeamActivity()
        }
    }

    suspend fun createTeam(
        request: CreateTeamRequest,
        userModel: UserEntity
    ): TeamActionResult {
        val teamTypeForValidation = if (request.category == "enterprise") "enterprise" else "team"
        if (teamsRepository.isTeamNameExists(request.name, teamTypeForValidation, null)) {
            return TeamActionResult.NameExists
        }
        return teamsRepository.createTeamAndAddMember(request, userModel)
            .fold(
                onSuccess = { TeamActionResult.Success },
                onFailure = { TeamActionResult.Failure(it.message) }
            )
    }

    suspend fun uploadTeamImage(uri: Uri): String? {
        return try {
            teamsRepository.uploadTeamImage(uri)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateExistingTeam(
        request: TeamUpdateRequest,
        category: String?
    ): TeamActionResult {
        val teamTypeForValidation = if (category == "enterprise") "enterprise" else "team"
        if (teamsRepository.isTeamNameExists(request.name, teamTypeForValidation, request.teamId)) {
            return TeamActionResult.NameExists
        }

        return teamsRepository.updateTeam(request)
            .fold(
            onSuccess = { updated ->
                if (updated) {
                    TeamActionResult.Success
                } else {
                    TeamActionResult.Failure(null)
                }
            },
            onFailure = { TeamActionResult.Failure(it.message) }
        )
    }
}
