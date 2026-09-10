package org.ole.planet.myplanet.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.effectiveId
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository

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

    private val _patientDetailState = MutableStateFlow(PatientDetailState(null, null))
    val patientDetailState: StateFlow<PatientDetailState> = _patientDetailState.asStateFlow()

    private val _isListLoading = MutableStateFlow(false)
    val isListLoading: StateFlow<Boolean> = _isListLoading.asStateFlow()




    private val _loggedInUser = MutableStateFlow<UserEntity?>(null)
    val loggedInUser: StateFlow<UserEntity?> = _loggedInUser.asStateFlow()

    private var searchJob: Job? = null
    private var selectPatientJob: Job? = null
    private var currentPatientId: String? = null

    fun loadPatients(sortBy: String = "joinDate", descending: Boolean = true) {
        viewModelScope.launch {
            _patientList.value = healthRepository.getPatientsSortedBy(sortBy, descending)
        }
    }

    fun searchPatients(query: String, sortBy: String = "joinDate", descending: Boolean = true) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val loadingJob = launch {
                delay(100)
                _isListLoading.value = true
            }
            val result = healthRepository.searchPatients(query, sortBy, descending)
            loadingJob.cancel()
            _patientList.value = result
            _isListLoading.value = false
        }
    }

    fun loadInitialPatient() {
        viewModelScope.launch {
            val currentUser = userRepository.getUserModel()
            _loggedInUser.value = currentUser
            val uid = currentUser?.effectiveId
            val normalizedId = uid?.trim()
            if (!normalizedId.isNullOrEmpty()) {
                selectPatient(normalizedId)
            }
        }
    }

    fun selectPatient(userId: String) {
        if (userId == currentPatientId && (selectPatientJob?.isActive == true || _patientDetailState.value.user != null)) {
            return
        }
        fetchPatientData(userId)
    }

    fun refreshSelectedPatient(userId: String? = null) {
        val targetId = userId?.trim()?.ifEmpty { null }
            ?: currentPatientId
            ?: _loggedInUser.value?.effectiveId?.trim()
        if (!targetId.isNullOrEmpty()) {
            fetchPatientData(targetId)
        } else {
            loadInitialPatient()
        }
    }

    private fun fetchPatientData(userId: String) {
        currentPatientId = userId
        selectPatientJob?.cancel()
        var job: Job? = null
        job = viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = healthRepository.getPatientById(userId)
                if (user != null) {
                    val record = healthRepository.getPatientHealthRecords(userId, user)
                    _patientDetailState.value = PatientDetailState(user, record)
                } else {
                    currentPatientId = null
                    _patientDetailState.value = PatientDetailState(null, null)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentPatientId = null
                _patientDetailState.value = PatientDetailState(null, null)
                throw e
            } finally {
                if (selectPatientJob === job) {
                    _isLoading.value = false
                }
            }
        }
        selectPatientJob = job
    }

    fun loadHealthData(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            coroutineScope {
                val userModelDeferred = async { userRepository.getUserById(userId) }
                val decodedHealthDeferred = async { healthRepository.getHealthProfile(userId) }

                val userModel = userModelDeferred.await()
                val decodedHealth = decodedHealthDeferred.await()

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
            }
            _isLoading.value = false
        }
    }

    fun saveHealthData(userId: String, userData: Map<String, Any?>) {
        viewModelScope.launch {
            healthRepository.updateUserHealthProfile(userId, userData)
            _isSaved.value = true
        }
    }
}

data class PatientDetailState(
    val user: UserEntity?,
    val healthRecord: HealthRecord?
)

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
