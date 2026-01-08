package org.ole.planet.myplanet.ui.teams.member

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.service.UserSessionManager

data class MembersUiState(
    val members: List<RealmUserModel> = emptyList(),
    val isLeader: Boolean = false,
    val memberCount: Int = 0
)

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MembersUiState())
    val uiState: StateFlow<MembersUiState> = _uiState.asStateFlow()
    private val _successAction = MutableSharedFlow<Unit>()
    val successAction = _successAction.asSharedFlow()

    fun fetchMembers(teamId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val members = teamsRepository.getRequestedMembers(teamId)
            val memberCount = teamsRepository.getJoinedMembers(teamId).size
            val isLeader = teamsRepository.isTeamLeader(teamId, userSessionManager.userModel?.id)
            _uiState.value = MembersUiState(members, isLeader, memberCount)
        }
    }

    fun respondToRequest(teamId: String?, user: RealmUserModel, isAccepted: Boolean) {
        if (teamId.isNullOrBlank() || user.id.isNullOrBlank()) return

        val originalState = _uiState.value
        val optimisticState = originalState.copy(
            members = originalState.members.filter { it.id != user.id }
        )
        _uiState.value = optimisticState

        viewModelScope.launch(Dispatchers.IO) {
            val result = teamsRepository.respondToMemberRequest(teamId, user.id!!, isAccepted)
            if (result.isSuccess) {
                teamsRepository.syncTeamActivities()
                _successAction.emit(Unit)
                fetchMembers(teamId)
            } else {
                _uiState.value = originalState
            }
        }
    }
}
