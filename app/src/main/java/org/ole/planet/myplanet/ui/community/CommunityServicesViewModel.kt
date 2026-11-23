package org.ole.planet.myplanet.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.repository.TeamsMembersRepository
import org.ole.planet.myplanet.repository.TeamsShareRepository

@HiltViewModel
class CommunityServicesViewModel @Inject constructor(
    private val teamsShareRepository: TeamsShareRepository,
    private val teamsMembersRepository: TeamsMembersRepository
) : ViewModel() {

    private val _teamLinks = MutableStateFlow<List<MyTeam>?>(null)
    val teamLinks: StateFlow<List<MyTeam>?> = _teamLinks.asStateFlow()

    init {
        loadTeamLinks()
    }

    fun loadTeamLinks() {
        viewModelScope.launch {
            _teamLinks.value = teamsShareRepository.getTeamLinks()
        }
    }

    suspend fun isMember(userId: String?, teamId: String): Boolean = teamsMembersRepository.isMember(userId, teamId)
}
