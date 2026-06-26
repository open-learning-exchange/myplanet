package org.ole.planet.myplanet.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _healthData = MutableStateFlow<HealthData?>(null)
    val healthData: StateFlow<HealthData?> = _healthData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    fun loadHealthData(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val userModel = userRepository.getUserById(userId)
            val decodedHealth = userRepository.getHealthProfile(userId)

            _healthData.value = HealthData(
                decodedHealth,
                userModel?.firstName,
                userModel?.middleName,
                userModel?.lastName,
                userModel?.email,
                userModel?.phoneNumber,
                userModel?.dob,
                userModel?.birthPlace
            )
            _isLoading.value = false
        }
    }

    fun saveHealthData(userId: String, userData: Map<String, Any?>) {
        viewModelScope.launch {
            userRepository.updateUserHealthProfile(userId, userData)
            _isSaved.value = true
        }
    }
}

data class HealthData(
    val myHealth: RealmMyHealth?,
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val email: String?,
    val phoneNumber: String?,
    val dob: String?,
    val birthPlace: String?
)
