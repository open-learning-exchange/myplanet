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
            withContext(dispatcherProvider.io) {
                teamsRepository.getTasksByTeamId(teamId).collectLatest { tasks ->
                    _taskList.value = tasks
                }
            }
        }
    }

    private var currentTeams: List<TeamSummary> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentUserId: String? = null
    private var loadJob: kotlinx.coroutines.Job? = null


    fun loadTeams(fromDashboard: Boolean, type: String?, userId: String?) {
        currentUserId = userId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                when {
                    fromDashboard -> {
                        if (userId != null) {
                            teamsRepository.getMyTeamsFlow(userId).collectLatest { list ->
                                val teamList = list.mapNotNull {
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
                                processTeams(teamList, userId, currentSearchQuery)
                            }
                        }
                    }
                    type == "enterprise" -> {
                        val teamList = teamsRepository.getShareableEnterpriseSummaries(null)
                        processTeams(teamList, userId, currentSearchQuery)
                    }
                    else -> {
                        val teamList = teamsRepository.getTeamSummaries(null)
                        processTeams(teamList, userId, currentSearchQuery)
                    }
                }
            }
        }
    }

    fun searchTeams(query: String) {
        currentSearchQuery = query
        viewModelScope.launch {
            processTeams(currentTeams, currentUserId, currentSearchQuery)
        }
    }

    private suspend fun processTeams(teams: List<TeamSummary>, userId: String?, searchQuery: String) {
        currentTeams = teams
        val processedTeams = withContext(dispatcherProvider.io) {
            val filteredList = if (searchQuery.isEmpty()) {
                teams
            } else {
                teams.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }
            }

            val validTeams = filteredList.filter {
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
            processTeams(currentTeams, userId, currentSearchQuery)
        }
    }

    fun leaveTeam(teamId: String, userId: String?) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                teamsRepository.leaveTeam(teamId, userId)
                teamsRepository.syncTeamActivities()
            }
            processTeams(currentTeams, userId, currentSearchQuery)
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
