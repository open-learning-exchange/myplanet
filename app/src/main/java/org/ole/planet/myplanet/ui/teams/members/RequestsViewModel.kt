package org.ole.planet.myplanet.ui.teams.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.TeamsMembersRepository
import org.ole.planet.myplanet.repository.UserRepository

data class RequestsUiState(
    val members: List<UserEntity> = emptyList(),
    val isLeader: Boolean = false,
    val memberCount: Int = 0
)

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val teamsRepository: TeamsMembersRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()
    private val _successAction = MutableSharedFlow<Unit>()
    val successAction = _successAction.asSharedFlow()

    fun fetchMembers(teamId: String) {
        viewModelScope.launch {
            coroutineScope {
                val membersDeferred = async { teamsRepository.getRequestedMembers(teamId) }
                val memberCountDeferred = async { teamsRepository.getJoinedMemberCount(teamId) }
                val userDeferred = async { userRepository.getUserModel() }
                val user = userDeferred.await()
                val isLeader = teamsRepository.isTeamLeader(teamId, user?.id)
                _uiState.value = RequestsUiState(
                    members = membersDeferred.await(),
                    isLeader = isLeader,
                    memberCount = memberCountDeferred.await()
                )
            }
        }
    }

    fun respondToRequest(teamId: String?, user: UserEntity, isAccepted: Boolean) {
        if (teamId.isNullOrBlank() || user.id.isNullOrBlank()) return

        val originalState = _uiState.value
        val optimisticState = originalState.copy(
            members = originalState.members.filter { it.id != user.id },
            memberCount = if (isAccepted) originalState.memberCount + 1 else originalState.memberCount
        )
        _uiState.value = optimisticState

        viewModelScope.launch {
            val userId = user.id ?: run { _uiState.value = originalState; return@launch }
            val result = teamsRepository.respondToMemberRequest(teamId, userId, isAccepted)
            if (result.isSuccess) {
                _successAction.emit(Unit)
                teamsRepository.recordTeamActivity()
            } else {
                _uiState.value = originalState
            }
        }
    }
}
