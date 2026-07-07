package org.ole.planet.myplanet.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _teams = MutableStateFlow<List<RealmMyTeam>>(emptyList())
    val teams: StateFlow<List<RealmMyTeam>> = _teams.asStateFlow()

    private val _users = MutableStateFlow<List<RealmUser>>(emptyList())
    val users: StateFlow<List<RealmUser>> = _users.asStateFlow()

    private val _savedUsers = MutableStateFlow<List<User>>(emptyList())
    val savedUsers: StateFlow<List<User>> = _savedUsers.asStateFlow()

    init {
        loadSavedUsers()
    }

    fun loadTeamsAsync(force: Boolean = false) {
        if (!force && _teams.value.isNotEmpty()) {
            return
        }
        viewModelScope.launch { // Launch on main to safely emit Realm objects
            val teamsList = teamsRepository.getAllActiveTeams()
            _teams.value = teamsList
        }
    }

    fun getTeamMembers(teamId: String?) {
        viewModelScope.launch { // Launch on main to safely emit Realm objects
            if (!teamId.isNullOrEmpty()) {
                val teamMembers = teamsRepository.refreshJoinedMembersForLogin(teamId)
                _users.value = teamMembers
                loadSavedUsers() // Refresh saved users after joining team
            } else {
                _users.value = emptyList()
            }
        }
    }

    fun loadSavedUsers() {
        viewModelScope.launch(dispatcherProvider.io) {
            _savedUsers.value = userRepository.getSavedUsers()
        }
    }

    fun saveUsers(name: String?, encryptedPassword: String?, source: String, userProfile: String?, userName: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            userRepository.saveSavedUser(name, encryptedPassword, source, userProfile, userName)
            loadSavedUsers()
        }
    }

    fun resetGuestAsMember(username: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            userRepository.resetGuestAsMember(username)
            loadSavedUsers()
        }
    }
}
