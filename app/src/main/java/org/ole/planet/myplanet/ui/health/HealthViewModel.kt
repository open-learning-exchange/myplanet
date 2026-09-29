package org.ole.planet.myplanet.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.effectiveId
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val healthRepository: HealthRepository,
    realtimeSyncManager: RealtimeSyncManager
) : ViewModel() {

    private val _healthData = MutableStateFlow<HealthData?>(null)
    val healthData: StateFlow<HealthData?> = _healthData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _saveFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveFailed: SharedFlow<Unit> = _saveFailed.asSharedFlow()


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

    @OptIn(FlowPreview::class)
    val healthSyncUpdates: Flow<Unit> = realtimeSyncManager.dataUpdateFlow
        .filter { it.table == HEALTH_TABLE && it.shouldRefreshUI }
        .debounce(SYNC_REFRESH_DEBOUNCE_MS)
        .map { }

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
                    clearPatientUnlessDisplayed(userId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                clearPatientUnlessDisplayed(userId)
            } finally {
                if (selectPatientJob === job) {
                    _isLoading.value = false
                }
            }
        }
        selectPatientJob = job
    }

    /**
     * Clears the detail view unless [userId] is the patient currently on screen: a failed or
     * empty re-read of the displayed patient is far more likely to be transient than a real
     * deletion, and blanking the screen for it loses data the user was reading. The trade-off is
     * that a patient genuinely deleted server-side keeps showing until another patient is picked.
     * The mirror case is deliberate too: a failed load for a *different* patient does clear the
     * display, since the user asked for that patient and showing the previous one would mislead.
     */
    private fun clearPatientUnlessDisplayed(userId: String) {
        val displayed = _patientDetailState.value.user
        val isDisplayedPatient = displayed != null &&
            (displayed.effectiveId == userId || displayed.id == userId)
        if (isDisplayedPatient) {
            return
        }
        currentPatientId = null
        _patientDetailState.value = PatientDetailState(null, null)
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
            try {
                healthRepository.updateUserHealthProfile(userId, userData)
                _isSaved.value = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _saveFailed.emit(Unit)
            }
        }
    }

    private companion object {
        const val HEALTH_TABLE = "health"
        const val SYNC_REFRESH_DEBOUNCE_MS = 200L
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
