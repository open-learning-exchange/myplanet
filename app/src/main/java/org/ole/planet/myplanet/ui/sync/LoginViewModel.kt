package org.ole.planet.myplanet.ui.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val userRepository: UserRepository,
    private val communityRepository: CommunityRepository,
    private val userSessionManager: UserSessionManager,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _teams = MutableStateFlow<List<MyTeam>>(emptyList())
    val teams: StateFlow<List<MyTeam>> = _teams.asStateFlow()

    private val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    val users: StateFlow<List<UserEntity>> = _users.asStateFlow()

    private val _savedUsers = MutableStateFlow<List<User>>(emptyList())
    val savedUsers: StateFlow<List<User>> = _savedUsers.asStateFlow()

    init {
        loadSavedUsers()
    }

    fun loadTeamsAsync(force: Boolean = false) {
        if (!force && _teams.value.isNotEmpty()) {
            return
        }
        viewModelScope.launch(dispatcherProvider.io) {
            val teamsList = teamsRepository.getAllActiveTeams()
            _teams.value = teamsList
        }
    }

    fun getTeamMembers(teamId: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
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

    fun saveUsers(name: String?, encryptedPassword: String?, source: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            val user = userRepository.getUserModel()
            userRepository.upsertSavedUser(name, encryptedPassword, source, user?.userImage ?: "", user?.name)
            loadSavedUsers()
        }
    }

    fun resetGuestAsMember(username: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            userRepository.resetGuestAsMember(username)
            loadSavedUsers()
        }
    }

    suspend fun getUserByName(name: String): UserEntity? = userRepository.getUserByName(name)

    suspend fun validateUsername(username: String): String? = userRepository.validateUsername(username)

    suspend fun findUserByName(name: String): UserEntity? = userRepository.findUserByName(name)

    enum class AuthOutcome {
        NoUsers,
        Success,
        Failed
    }

    suspend fun loginAsGuest(username: String): UserEntity? {
        val model = userRepository.createGuestUser(username)
        if (model != null) {
            userSessionManager.saveUserInfoPref("", model)
        }
        return model
    }

    suspend fun authenticate(username: String?, password: String?, isManagerMode: Boolean): AuthOutcome {
        return try {
            if (!userRepository.hasAtLeastOneUser()) {
                AuthOutcome.NoUsers
            } else {
                val user = userRepository.authenticateUser(username, password, isManagerMode)
                if (user != null) {
                    userSessionManager.saveUserInfoPref(password, user)
                    AuthOutcome.Success
                } else {
                    AuthOutcome.Failed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("LoginViewModel", "Failed to authenticate user", e)
            AuthOutcome.Failed
        }
    }

    private var communityDocsSynced = false

    suspend fun syncCommunityDocs(): Boolean {
        if (communityDocsSynced) {
            return true
        }
        val result = withContext(dispatcherProvider.io) {
            communityRepository.syncCommunityDocs()
        }
        if (result) {
            communityDocsSynced = true
        }
        return result
    }
}
