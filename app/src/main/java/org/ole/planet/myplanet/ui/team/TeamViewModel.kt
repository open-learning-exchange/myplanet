package org.ole.planet.myplanet.ui.team

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.data.team.TeamRepository
import org.ole.planet.myplanet.model.RealmMyTeam

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _teams = MutableStateFlow<List<RealmMyTeam>>(emptyList())
    val teams: StateFlow<List<RealmMyTeam>> = _teams.asStateFlow()

    fun loadTeams(fromDashboard: Boolean, type: String?, settings: SharedPreferences?) {
        viewModelScope.launch {
            _teams.value = teamRepository.getTeams(fromDashboard, type, settings)
        }
    }

    suspend fun searchTeams(query: String, type: String?): List<RealmMyTeam> {
        return teamRepository.searchTeams(query, type)
    }
}
