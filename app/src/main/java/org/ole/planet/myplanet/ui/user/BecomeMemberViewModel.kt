package org.ole.planet.myplanet.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MemberInfo
import org.ole.planet.myplanet.repository.UserRepository

data class UsernameCheck(val input: String, val error: String?)

@HiltViewModel
class BecomeMemberViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _usernameChecks = MutableSharedFlow<UsernameCheck>(extraBufferCapacity = 1)
    val usernameChecks: SharedFlow<UsernameCheck> = _usernameChecks.asSharedFlow()

    private var usernameValidationJob: Job? = null

    suspend fun createMember(info: MemberInfo) = userRepository.createMember(info)

    suspend fun validateUsername(username: String) = userRepository.validateUsername(username)

    suspend fun cleanupDuplicateUsers() = userRepository.cleanupDuplicateUsers()

    fun onUsernameChanged(input: String) {
        usernameValidationJob?.cancel()
        usernameValidationJob = viewModelScope.launch {
            delay(300)
            _usernameChecks.emit(UsernameCheck(input, userRepository.validateUsername(input)))
        }
    }
}
