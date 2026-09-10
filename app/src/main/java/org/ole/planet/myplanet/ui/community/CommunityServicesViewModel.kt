package org.ole.planet.myplanet.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class CommunityServicesViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _teamLinks = MutableStateFlow<List<MyTeam>>(emptyList())
    val teamLinks: StateFlow<List<MyTeam>> = _teamLinks.asStateFlow()

    init {
        loadTeamLinks()
    }

    fun loadTeamLinks() {
        viewModelScope.launch(dispatcherProvider.io) {
            _teamLinks.value = teamsRepository.getTeamLinks()
        }
    }

    suspend fun isMember(userId: String?, teamId: String): Boolean = withContext(dispatcherProvider.io) {
        teamsRepository.isMember(userId, teamId)
    }
}
