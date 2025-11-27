package org.ole.planet.myplanet.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.team.teamMember.MemberRequest
import javax.inject.Inject

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val userProfileDbHandler: UserProfileDbHandler
) : ViewModel() {

    private val _memberRequests = MutableStateFlow<List<MemberRequest>>(emptyList())
    val memberRequests: StateFlow<List<MemberRequest>> = _memberRequests

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadMemberRequests(teamId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val currentUser = userProfileDbHandler.userModel
                _memberRequests.value = teamRepository.getMemberRequests(teamId, currentUser?.id)
            } catch (e: Exception) {
                _error.value = "Failed to load member requests: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun respondToMemberRequest(teamId: String, userId: String, accept: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                teamRepository.respondToMemberRequest(teamId, userId, accept).getOrThrow()
                teamRepository.syncTeamActivities()
                loadMemberRequests(teamId)
            } catch (e: Exception) {
                _error.value = "Failed to respond to member request: ${e.message}"
                loadMemberRequests(teamId)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
