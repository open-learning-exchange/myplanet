package org.ole.planet.myplanet.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.HealthRecord
import android.text.TextUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val healthRepository: HealthRepository
) : ViewModel() {

    private val _healthData = MutableStateFlow<HealthData?>(null)
    val healthData: StateFlow<HealthData?> = _healthData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()


    private val _patientList = MutableStateFlow<List<UserEntity>>(emptyList())
    val patientList: StateFlow<List<UserEntity>> = _patientList.asStateFlow()

    private val _selectedPatient = MutableStateFlow<UserEntity?>(null)
    val selectedPatient: StateFlow<UserEntity?> = _selectedPatient.asStateFlow()

    private val _healthRecord = MutableStateFlow<HealthRecord?>(null)
    val healthRecord: StateFlow<HealthRecord?> = _healthRecord.asStateFlow()

    private val _isListLoading = MutableStateFlow(false)
    val isListLoading: StateFlow<Boolean> = _isListLoading.asStateFlow()



    private var searchJob: Job? = null

    fun loadPatients(sortBy: String = "joinDate", descending: Boolean = true) {
        viewModelScope.launch {
            _patientList.value = healthRepository.getPatientsSortedBy(sortBy, descending)
        }
    }

    fun searchPatients(query: String, sortBy: String = "joinDate", descending: Boolean = true) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _isListLoading.value = true
            _patientList.value = healthRepository.searchPatients(query, sortBy, descending)
            _isListLoading.value = false
        }
    }

    fun loadInitialPatient() {
        viewModelScope.launch {
            val currentUser = userRepository.getUserModel()
            val uid = if (currentUser?._id.isNullOrEmpty()) currentUser?.id else currentUser?._id
            val normalizedId = uid?.trim()
            if (!normalizedId.isNullOrEmpty()) {
                selectPatient(normalizedId)
            }
        }
    }

    fun selectPatient(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val user = healthRepository.getPatientById(userId)
            _selectedPatient.value = user
            if (user != null) {
                _healthRecord.value = healthRepository.getPatientHealthRecords(userId, user)
            } else {
                _healthRecord.value = null
            }
            _isLoading.value = false
        }
    }

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
    val myHealth: MyHealth?,
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val email: String?,
    val phoneNumber: String?,
    val dob: String?,
    val birthPlace: String?
)
