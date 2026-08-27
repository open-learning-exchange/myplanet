package org.ole.planet.myplanet.ui.teams.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

    private val _uiState = MutableStateFlow(TeamResourcesUiState())
    val uiState: StateFlow<TeamResourcesUiState> = _uiState.asStateFlow()

    fun loadResources(teamId: String, userId: String?) {
        viewModelScope.launch {
            val libraries = teamsRepository.getTeamResources(teamId)
            val canRemove = teamsRepository.isTeamLeader(teamId, userId)
            _uiState.value = TeamResourcesUiState(resources = libraries, canRemove = canRemove)
        }
    }

    suspend fun addResources(teamId: String, resources: List<TeamResourceDto>, userId: String?) {
        teamsRepository.addResourceLinks(teamId, resources, userId)
        teamsRepository.recordTeamActivity()
    }

    suspend fun removeResource(teamId: String, resourceId: String) {
        teamsRepository.removeResourceLink(teamId, resourceId)
        teamsRepository.recordTeamActivity()
    }

    suspend fun getAvailableResources(teamId: String): List<MyLibrary> {
        return teamsRepository.getAvailableResourcesToAdd(teamId)
    }
}
