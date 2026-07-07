package org.ole.planet.myplanet.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val sharedPrefManager: SharedPrefManager,
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
        viewModelScope.launch {
            val teamsList = withContext(dispatcherProvider.io) {
                teamsRepository.getAllActiveTeams()
            }
            _teams.value = teamsList
        }
    }

    fun getTeamMembers(teamId: String?) {
        viewModelScope.launch {
            if (!teamId.isNullOrEmpty()) {
                val teamMembers = withContext(dispatcherProvider.io) {
                    teamsRepository.refreshJoinedMembersForLogin(teamId)
                }
                _users.value = teamMembers
                loadSavedUsers() // Refresh saved users after joining team
            } else {
                _users.value = emptyList()
            }
        }
    }

    fun loadSavedUsers() {
        viewModelScope.launch(dispatcherProvider.io) {
            _savedUsers.value = sharedPrefManager.getSavedUsers()
        }
    }

    fun saveUsers(name: String?, encryptedPassword: String?, source: String, userProfile: String?, userName: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            val existingUsers: MutableList<User> = ArrayList(sharedPrefManager.getSavedUsers())
            if (source == "guest") {
                val newUser = User("", name, encryptedPassword, "", "guest")
                var newUserIndex = -1
                for (i in existingUsers.indices) {
                    if (existingUsers[i].name == newUser.name?.trim { it <= ' ' }) {
                        newUserIndex = i
                        break
                    }
                }
                if (newUserIndex != -1) {
                    existingUsers[newUserIndex] = newUser
                } else {
                    existingUsers.add(newUser)
                }
                sharedPrefManager.setSavedUsers(existingUsers)
            } else if (source == "member") {

                val newUser = User(userName, name, encryptedPassword, userProfile, "member")
                var newUserIndex = -1
                for (i in existingUsers.indices) {
                    if (existingUsers[i].fullName == newUser.fullName?.trim { it <= ' ' }) {
                        newUserIndex = i
                        break
                    }
                }
                if (newUserIndex != -1) {
                    existingUsers[newUserIndex] = newUser
                } else {
                    existingUsers.add(newUser)
                }
                sharedPrefManager.setSavedUsers(existingUsers)
            }
            loadSavedUsers()
        }
    }

    fun resetGuestAsMember(username: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            val existingUsers = sharedPrefManager.getSavedUsers().toMutableList()
            var newUserExists = false
            for ((_, name) in existingUsers) {
                if (name == username) {
                    newUserExists = true
                    break
                }
            }
            if (newUserExists) {
                val iterator = existingUsers.iterator()
                while (iterator.hasNext()) {
                    val (_, name) = iterator.next()
                    if (name == username) {
                        iterator.remove()
                    }
                }
                sharedPrefManager.setSavedUsers(existingUsers)
                loadSavedUsers()
            }
        }
    }
}
