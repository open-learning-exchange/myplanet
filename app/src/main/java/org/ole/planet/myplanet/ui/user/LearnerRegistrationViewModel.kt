package org.ole.planet.myplanet.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.LearnerRegistrationInfo
import org.ole.planet.myplanet.repository.UserRepository

data class UsernameCheck(val input: String, val error: String?)

sealed interface RegistrationState {
    data object Idle : RegistrationState
    data object Creating : RegistrationState
    data class Created(val info: LearnerRegistrationInfo, val message: String) : RegistrationState
    data class Failed(val message: String) : RegistrationState
}

@HiltViewModel
class LearnerRegistrationViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _usernameChecks = MutableSharedFlow<UsernameCheck>(extraBufferCapacity = 1)
    val usernameChecks: SharedFlow<UsernameCheck> = _usernameChecks.asSharedFlow()

    private var usernameValidationJob: Job? = null

    private val _registration = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registration: StateFlow<RegistrationState> = _registration.asStateFlow()

    suspend fun createMember(info: LearnerRegistrationInfo) = userRepository.createMember(info)

    fun register(info: LearnerRegistrationInfo) {
        val current = _registration.value
        if (current is RegistrationState.Creating || current is RegistrationState.Created) return
        _registration.value = RegistrationState.Creating
        viewModelScope.launch {
            _registration.value = try {
                val (created, message) = userRepository.createMember(info)
                if (created) RegistrationState.Created(info, message) else RegistrationState.Failed(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RegistrationState.Failed(e.localizedMessage.orEmpty())
            }
        }
    }

    fun failureShown() {
        if (_registration.value is RegistrationState.Failed) _registration.value = RegistrationState.Idle
    }

    suspend fun validateUsername(username: String) = userRepository.validateUsername(username)

    suspend fun cleanupDuplicateUsers() = userRepository.cleanupDuplicateUsers()

    fun onUsernameChanged(input: String) {
        usernameValidationJob?.cancel()
        usernameValidationJob = viewModelScope.launch {
            delay(300.milliseconds)
            _usernameChecks.emit(UsernameCheck(input, userRepository.validateUsername(input)))
        }
    }
}
