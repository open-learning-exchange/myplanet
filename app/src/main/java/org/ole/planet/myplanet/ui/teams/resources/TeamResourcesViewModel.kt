package org.ole.planet.myplanet.ui.teams.resources

import android.util.Log
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

    private val _uiState = MutableStateFlow<TeamResourcesUiState?>(null)
    val uiState: StateFlow<TeamResourcesUiState?> = _uiState.asStateFlow()

    fun loadResources(teamId: String, userId: String?) {
        viewModelScope.launch {
            val libraries = teamsRepository.getTeamResources(teamId)
            val canRemove = teamsRepository.isTeamLeader(teamId, userId)
            _uiState.value = TeamResourcesUiState(resources = libraries, canRemove = canRemove)
        }
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
            .onFailure { Log.w(TAG, "Failed to record team activity", it) }
    }

    private companion object {
        const val TAG = "TeamResourcesViewModel"
    }
}
