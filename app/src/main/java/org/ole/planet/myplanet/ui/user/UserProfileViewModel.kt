package org.ole.planet.myplanet.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UserProfileService

sealed class ProfileUpdateState {
    object Idle : ProfileUpdateState()
    object Success : ProfileUpdateState()
    data class Error(val message: String) : ProfileUpdateState()
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userProfileDbHandler: UserProfileService,
) : ViewModel() {

    private val _userModel = MutableStateFlow<RealmUserModel?>(null)
    val userModel: StateFlow<RealmUserModel?> = _userModel.asStateFlow()

    private val _updateState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)
    val updateState: StateFlow<ProfileUpdateState> = _updateState.asStateFlow()

    fun loadUserProfile(userId: String?) {
        if (userId.isNullOrBlank()) return
        viewModelScope.launch {
            _userModel.value = userRepository.getUserByAnyId(userId)
        }
    }

    fun refreshUserProfile(userId: String?) {
        loadUserProfile(userId)
    }

    fun updateUserProfile(
        userId: String?,
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
        if (userId.isNullOrBlank()) {
            _updateState.value = ProfileUpdateState.Error("Invalid user id")
            return
        }

        viewModelScope.launch {
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

    fun updateProfileImage(userId: String?, imagePath: String?) {
        if (userId.isNullOrBlank()) {
            _updateState.value = ProfileUpdateState.Error("Invalid user id")
            return
        }

        viewModelScope.launch {
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

    val lastVisit: Long?
        get() = userProfileDbHandler.lastVisit

    val offlineVisits: Int
        get() = userProfileDbHandler.offlineVisits

    val numberOfResourceOpen: String
        get() = userProfileDbHandler.numberOfResourceOpen

    private val _maxOpenedResource = MutableStateFlow("")
    val maxOpenedResource: StateFlow<String> = _maxOpenedResource.asStateFlow()

    init {
        viewModelScope.launch {
            _maxOpenedResource.value = userProfileDbHandler.maxOpenedResource()
        }
    }
}
