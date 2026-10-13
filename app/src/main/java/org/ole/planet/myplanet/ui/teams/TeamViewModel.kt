package org.ole.planet.myplanet.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.CreateTeamRequest
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamStatus
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider

sealed class TeamActionResult {
    object Success : TeamActionResult()
    data class Failure(val message: String?) : TeamActionResult()
    object NameExists : TeamActionResult()
}

enum class TeamJoinState {
    UNKNOWN,
    JOINABLE,
    PENDING,
    LEAVE
}

sealed class TeamDetailState {
    object Loading : TeamDetailState()
    data class Success(val team: MyTeam) : TeamDetailState()
    object NotFound : TeamDetailState()
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

    private val _teamDetailState = MutableStateFlow<TeamDetailState>(TeamDetailState.Loading)
    val teamDetailState: StateFlow<TeamDetailState> = _teamDetailState.asStateFlow()

    private val _memberCount = MutableStateFlow<Int?>(null)
    val memberCount: StateFlow<Int?> = _memberCount.asStateFlow()

    private val _joinState = MutableStateFlow<TeamJoinState>(TeamJoinState.UNKNOWN)
    val joinState: StateFlow<TeamJoinState> = _joinState.asStateFlow()

    fun getTeamUpdateFlow() = realtimeSyncManager.updatesFor("teams")

    fun loadTeamDetail(
        primaryTeamId: String,
        fallbackTeamId: String? = null,
        isMyTeam: Boolean = false,
        userId: String? = null
    ) {
        if (_teamDetailState.value !is TeamDetailState.Success) {
            _teamDetailState.value = TeamDetailState.Loading
        }
        viewModelScope.launch {
            val resolvedTeam = withContext(dispatcherProvider.io) {
                when {
                    primaryTeamId.isNotEmpty() -> teamsRepository.getTeamByIdOrTeamId(primaryTeamId)
                    !fallbackTeamId.isNullOrEmpty() -> teamsRepository.getTeamById(fallbackTeamId)
                    else -> null
                }
            }

            if (resolvedTeam == null) {
                _teamDetailState.value = TeamDetailState.NotFound
                _memberCount.value = 0
                _joinState.value = if (isMyTeam) TeamJoinState.LEAVE else TeamJoinState.JOINABLE
                return@launch
            }

            val teamId = resolvedTeam._id
            val count = if (!teamId.isNullOrEmpty()) {
                withContext(dispatcherProvider.io) {
                    teamsRepository.getJoinedMemberCount(teamId)
                }
            } else {
                0
            }
            _memberCount.value = count

            val pending = if (!teamId.isNullOrEmpty() && !userId.isNullOrEmpty()) {
                withContext(dispatcherProvider.io) {
                    teamsRepository.hasPendingRequest(teamId, userId)
                }
            } else {
                false
            }

            _joinState.value = when {
                isMyTeam -> TeamJoinState.LEAVE
                pending -> TeamJoinState.PENDING
                else -> TeamJoinState.JOINABLE
            }

            _teamDetailState.value = TeamDetailState.Success(resolvedTeam)
        }
    }

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
        _joinState.value = TeamJoinState.PENDING
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
            recordTeamActivity()
            if (currentUserId != null) {
                loadTeams(currentFromDashboard, currentType, currentUserId)
            }
        }
    }

    fun leaveTeam(teamId: String, userId: String?) {
        _joinState.value = TeamJoinState.JOINABLE
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                teamsRepository.leaveTeam(teamId, userId)
                teamsRepository.recordTeamActivity()
            }
            if (currentUserId != null) {
                loadTeams(currentFromDashboard, currentType, currentUserId)
            }
        }
    }

    fun recordTeamActivity() {
        viewModelScope.launch {
            teamsRepository.recordTeamActivity()
        }
    }

    fun logTeamVisit(
        teamId: String,
        userName: String?,
        userPlanetCode: String?,
        userParentCode: String?,
        teamType: String?
    ) {
        viewModelScope.launch {
            teamsRepository.logTeamVisit(teamId, userName, userPlanetCode, userParentCode, teamType)
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
        userModel: UserEntity
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

    suspend fun updateExistingTeam(
        teamId: String,
        name: String,
        description: String,
        services: String,
        rules: String,
        category: String?,
        updatedBy: String?
    ): TeamActionResult {
        val teamTypeForValidation = if (category == "enterprise") "enterprise" else "team"
        if (teamsRepository.isTeamNameExists(name, teamTypeForValidation, teamId)) {
            return TeamActionResult.NameExists
        }

        return teamsRepository.updateTeam(
            teamId = teamId,
            name = name,
            description = description,
            services = services,
            rules = rules,
            updatedBy = updatedBy,
        ).fold(
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
