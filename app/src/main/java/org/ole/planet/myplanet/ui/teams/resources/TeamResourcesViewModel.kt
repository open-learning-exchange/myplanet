package org.ole.planet.myplanet.ui.teams.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.TeamResourceDto
import org.ole.planet.myplanet.repository.TeamsRepository

data class TeamResourcesUiState(
    val resources: List<MyLibrary> = emptyList(),
    val canRemove: Boolean = false
)

@HiltViewModel
class TeamResourcesViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TeamResourcesUiState?>(null)
    val uiState: StateFlow<TeamResourcesUiState?> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastTeamId: String? = null
    private var lastUserId: String? = null

    fun loadResources(teamId: String, userId: String?, force: Boolean = false) {
        if (!force && loadJob?.isActive == true && teamId == lastTeamId && userId == lastUserId) {
            return
        }
        loadJob?.cancel()
        lastTeamId = teamId
        lastUserId = userId
        loadJob = viewModelScope.launch {
            coroutineScope {
                val librariesDeferred = async { teamsRepository.getTeamResources(teamId) }
                val canRemoveDeferred = async { teamsRepository.isTeamLeader(teamId, userId) }
                _uiState.value = TeamResourcesUiState(
                    resources = librariesDeferred.await(),
                    canRemove = canRemoveDeferred.await()
                )
            }
        }
    }

    fun reload(teamId: String, userId: String?) {
        loadResources(teamId, userId, force = true)
    }

    suspend fun addResources(teamId: String, resources: List<TeamResourceDto>, userId: String?) {
        teamsRepository.addResourceLinks(teamId, resources, userId)
        recordActivitySafely()
    }

    suspend fun removeResource(teamId: String, resourceId: String) {
        teamsRepository.removeResourceLink(teamId, resourceId)
        recordActivitySafely()
    }

    suspend fun getAvailableResources(teamId: String): List<MyLibrary> {
        return teamsRepository.getAvailableResourcesToAdd(teamId)
    }

    private suspend fun recordActivitySafely() {
        runCatching { teamsRepository.recordTeamActivity() }
    }
}
