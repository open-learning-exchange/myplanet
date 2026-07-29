package org.ole.planet.myplanet.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.UserSessionManager

sealed class ProfileUpdateState {
    object Idle : ProfileUpdateState()
    object Success : ProfileUpdateState()
    data class Error(val message: String) : ProfileUpdateState()
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userSessionManager: UserSessionManager,
    private val activitiesRepository: ActivitiesRepository
) : ViewModel() {

    private val _userModel = MutableStateFlow<UserEntity?>(null)
    val userModel: StateFlow<UserEntity?> = _userModel.asStateFlow()

    private val _updateState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)
    val updateState: StateFlow<ProfileUpdateState> = _updateState.asStateFlow()

    fun loadCurrentUserProfile() {
        viewModelScope.launch {
            val userId = userRepository.getActiveUserIdSuspending()
            if (userId.isBlank()) return@launch
            _userModel.value = userRepository.getUserByAnyId(userId)
        }
    }

    fun refreshCurrentUserProfile() {
        loadCurrentUserProfile()
    }

    fun updateCurrentUserProfile(
        firstName: String?,
        lastName: String?,
        middleName: String?,
        email: String?,
        phoneNumber: String?,
        level: String?,
        language: String?,
        gender: String?,
        dob: String?,
    ) {
        viewModelScope.launch {
            val userId = userRepository.getActiveUserIdSuspending()
            if (userId.isBlank()) {
                _updateState.value = ProfileUpdateState.Error("Invalid user id")
                return@launch
            }

            runCatching {
                userRepository.updateUserDetails(
                    userId = userId,
                    firstName = firstName,
                    lastName = lastName,
                    middleName = middleName,
                    email = email,
                    phoneNumber = phoneNumber,
                    level = level,
                    language = language,
                    gender = gender,
                    dob = dob,
                )
            }.onSuccess { updatedUser ->
                updatedUser?.let { _userModel.value = it }
                _updateState.value = ProfileUpdateState.Success
            }.onFailure { throwable ->
                _updateState.value = ProfileUpdateState.Error(
                    throwable.message ?: "Unable to update profile",
                )
            }
        }
    }

    fun updateCurrentUserProfileImage(imagePath: String?) {
        viewModelScope.launch {
            val userId = userRepository.getActiveUserIdSuspending()
            if (userId.isBlank()) {
                _updateState.value = ProfileUpdateState.Error("Invalid user id")
                return@launch
            }

            runCatching { userRepository.updateUserImage(userId, imagePath) }
                .onSuccess { updatedUser ->
                    updatedUser?.let { _userModel.value = it }
                    _updateState.value = ProfileUpdateState.Success
                }
                .onFailure { throwable ->
                    _updateState.value = ProfileUpdateState.Error(
                        throwable.message ?: "Unable to update profile image",
                    )
                }
        }
    }

    fun resetUpdateState() {
        _updateState.value = ProfileUpdateState.Idle
    }

    private val _lastVisit = MutableStateFlow<Long?>(null)
    val lastVisit: StateFlow<Long?> = _lastVisit.asStateFlow()

    private val _offlineVisits = MutableStateFlow(0)
    val offlineVisits: StateFlow<Int> = _offlineVisits.asStateFlow()

    private val _numberOfResourceOpen = MutableStateFlow("")
    val numberOfResourceOpen: StateFlow<String> = _numberOfResourceOpen.asStateFlow()

    private val _maxOpenedResource = MutableStateFlow("")
    val maxOpenedResource: StateFlow<String> = _maxOpenedResource.asStateFlow()

    init {
        viewModelScope.launch {
            val fullName = userSessionManager.getUserModel()?.name ?: ""
            val result = activitiesRepository.getMostOpenedResource(fullName, UserSessionManager.KEY_RESOURCE_OPEN)
            _maxOpenedResource.value = if (result == null) "" else "${result.first} opened ${result.second} times"
            _lastVisit.value = activitiesRepository.getGlobalLastVisit()

            val count = activitiesRepository.getResourceOpenCount(fullName, UserSessionManager.KEY_RESOURCE_OPEN)
            _numberOfResourceOpen.value = if (count == 0L) "" else "Resource opened $count times."
        }
    }

    fun getOfflineVisits() {
        viewModelScope.launch {
            val user = userSessionManager.getUserModel()
            _offlineVisits.value = user?.id?.let { activitiesRepository.getOfflineVisitCount(it) } ?: 0
        }
    }
}
