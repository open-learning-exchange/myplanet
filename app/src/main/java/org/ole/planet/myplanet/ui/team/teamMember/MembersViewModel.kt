package org.ole.planet.myplanet.ui.team.teamMember

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler

data class MembersUiState(
    val members: List<RealmUserModel> = emptyList(),
    val isLeader: Boolean = false,
    val memberCount: Int = 0
)

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val userProfileDbHandler: UserProfileDbHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(MembersUiState())
    val uiState: StateFlow<MembersUiState> = _uiState

    fun fetchMembers(teamId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val members = teamRepository.getRequestedMembers(teamId)
            val memberCount = teamRepository.getJoinedMembers(teamId).size
            val isLeader = teamRepository.isTeamLeader(teamId, userProfileDbHandler.userModel?.id)
            _uiState.value = MembersUiState(members, isLeader, memberCount)
        }
    }
}
