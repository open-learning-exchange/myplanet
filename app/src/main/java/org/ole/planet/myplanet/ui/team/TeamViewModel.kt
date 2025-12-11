package org.ole.planet.myplanet.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.TeamRepository
import javax.inject.Inject

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamRepository: TeamRepository
) : ViewModel() {
    private val _events = MutableSharedFlow<TeamAction>()
    val events = _events.asSharedFlow()

    fun leaveTeam(teamId: String, userId: String?) {
        viewModelScope.launch {
            teamRepository.leaveTeam(teamId, userId)
            _events.emit(TeamAction.LeaveTeam)
        }
    }
}

sealed class TeamAction {
    object LeaveTeam : TeamAction()
}
