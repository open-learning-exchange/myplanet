package org.ole.planet.myplanet.ui.teams.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.JoinedMemberData
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.TeamsMembersRepository
import org.ole.planet.myplanet.repository.UserRepository

data class RequestsUiState(
    val members: List<UserEntity> = emptyList(),
    val isLeader: Boolean = false,
    val memberCount: Int = 0
)

data class MembersUiState(
    val members: List<JoinedMemberData> = emptyList(),
    val currentUserId: String? = null,
    val isLeader: Boolean = false
)

enum class MemberAction {
    LEAVE_TEAM,
    REMOVE_MEMBER,
    MAKE_LEADER
}

sealed interface MemberActionResult {
    data object LeftTeam : MemberActionResult
    data object MemberRemoved : MemberActionResult
    data object CannotRemoveLastLeader : MemberActionResult
    data object LeaderChanged : MemberActionResult
    data class Failed(val action: MemberAction, val message: String?) : MemberActionResult
}

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val teamsRepository: TeamsMembersRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()
    private val _successAction = MutableSharedFlow<Unit>()
    val successAction = _successAction.asSharedFlow()

    private val _membersState = MutableStateFlow(MembersUiState())
    val membersState: StateFlow<MembersUiState> = _membersState.asStateFlow()

    private val _actionResults = MutableSharedFlow<MemberActionResult>()
    val actionResults: SharedFlow<MemberActionResult> = _actionResults.asSharedFlow()

    fun loadJoinedMembers(teamId: String) {
        viewModelScope.launch {
            val members = teamsRepository.getJoinedMembersWithVisitInfo(teamId)
            val currentUserId = userRepository.getUserModel()?.id
            _membersState.value = MembersUiState(
                members = members,
                currentUserId = currentUserId,
                isLeader = members.any { it.user.id == currentUserId && it.isLeader }
            )
        }
    }

    fun leaveTeam(teamId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = userRepository.getUserModel()?.id
                val nextLeader = teamsRepository.getNextLeaderCandidate(teamId, currentUserId)
                nextLeader?.id?.let { teamsRepository.updateTeamLeader(teamId, it) }
                currentUserId?.let { teamsRepository.removeMember(teamId, it) }
                loadJoinedMembers(teamId)
                _actionResults.emit(MemberActionResult.LeftTeam)
            } catch (e: Exception) {
                _actionResults.emit(MemberActionResult.Failed(MemberAction.LEAVE_TEAM, e.message))
            }
        }
    }

    fun removeMember(teamId: String, memberId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = userRepository.getUserModel()?.id
                if (currentUserId == memberId) {
                    val nextLeader = teamsRepository.getNextLeaderCandidate(teamId, memberId)
                    if (nextLeader != null) {
                        nextLeader.id?.let { teamsRepository.updateTeamLeader(teamId, it) }
                    } else {
                        _actionResults.emit(MemberActionResult.CannotRemoveLastLeader)
                        return@launch
                    }
                }
                teamsRepository.removeMember(teamId, memberId)
                loadJoinedMembers(teamId)
                _actionResults.emit(MemberActionResult.MemberRemoved)
            } catch (e: Exception) {
                _actionResults.emit(MemberActionResult.Failed(MemberAction.REMOVE_MEMBER, e.message))
            }
        }
    }

    fun makeLeader(teamId: String, userId: String) {
        viewModelScope.launch {
            try {
                teamsRepository.updateTeamLeader(teamId, userId)
                loadJoinedMembers(teamId)
                _actionResults.emit(MemberActionResult.LeaderChanged)
            } catch (e: Exception) {
                _actionResults.emit(MemberActionResult.Failed(MemberAction.MAKE_LEADER, e.message))
            }
        }
    }

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
