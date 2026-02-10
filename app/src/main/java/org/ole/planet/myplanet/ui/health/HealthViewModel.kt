package org.ole.planet.myplanet.ui.health

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Sort
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnBaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager
import javax.inject.Inject

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val syncManager: SyncManager,
    private val userSessionManager: UserSessionManager,
    private val serverUrlMapper: ServerUrlMapper,
    private val prefManager: SharedPrefManager,
    @AppPreferences private val settings: SharedPreferences
) : ViewModel() {

    private val _healthRecord = MutableStateFlow<HealthRecord?>(null)
    val healthRecord: StateFlow<HealthRecord?> = _healthRecord.asStateFlow()

    private val _usersList = MutableStateFlow<List<RealmUser>>(emptyList())
    val usersList: StateFlow<List<RealmUser>> = _usersList.asStateFlow()

    private val _selectedUser = MutableStateFlow<RealmUser?>(null)
    val selectedUser: StateFlow<RealmUser?> = _selectedUser.asStateFlow()

    private val _canAddPatient = MutableStateFlow(false)
    val canAddPatient: StateFlow<Boolean> = _canAddPatient.asStateFlow()

    val syncStatus: StateFlow<SyncManager.SyncStatus> = syncManager.syncStatus

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val syncManagerInstance = RealtimeSyncManager.getInstance()
    private val onRealtimeSyncListener = object : OnBaseRealtimeSyncListener() {
        override fun onTableDataUpdated(update: TableDataUpdate) {
            if (update.table == "health" && update.shouldRefreshUI) {
                viewModelScope.launch {
                    refreshHealthData()
                }
            }
        }
    }

    private var searchJob: Job? = null

    init {
        syncManagerInstance.addListener(onRealtimeSyncListener)
        initializeUser()
    }

    private fun initializeUser() {
        viewModelScope.launch {
            val currentUser = userSessionManager.getUserModelCopy()
            if (currentUser != null) {
                _canAddPatient.value = currentUser.rolesList?.contains("health") == true
                val userId = if (currentUser._id.isNullOrEmpty()) currentUser.id else currentUser._id
                selectUser(userId)
            }
        }
    }

    fun startHealthSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isHealthSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        viewModelScope.launch {
            val serverUrl = settings.getString("serverURL", "") ?: ""
            val mapping = serverUrlMapper.processUrl(serverUrl)

            serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
                MainApplication.isServerReachable(url)
            }

            syncManager.start(object : OnSyncListener {
                override fun onSyncStarted() {}
                override fun onSyncComplete() {}
                override fun onSyncFailed(msg: String?) {}
            }, "full", listOf("health"))
        }
    }

    fun selectUser(userId: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val normalizedId = userId?.trim()
                if (normalizedId.isNullOrEmpty()) {
                    _selectedUser.value = null
                    _healthRecord.value = null
                    return@launch
                }

                val user = userRepository.getUserByAnyId(normalizedId)
                _selectedUser.value = user

                if (user != null) {
                    val record = userRepository.getHealthRecordsAndAssociatedUsers(normalizedId, user)
                    _healthRecord.value = record
                } else {
                    _healthRecord.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _healthRecord.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshHealthData() {
        val user = _selectedUser.value
        val userId = if (user?._id.isNullOrEmpty()) user?.id else user?._id
        selectUser(userId)
    }

    fun loadUsers(sortBy: String, sort: Sort) {
        viewModelScope.launch {
            val users = userRepository.getUsersSortedBy(sortBy, sort)
            _usersList.value = users
        }
    }

    fun searchUsers(query: String, sortBy: String, sort: Sort) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val users = userRepository.searchUsers(query, sortBy, sort)
            _usersList.value = users
        }
    }

    override fun onCleared() {
        syncManagerInstance.removeListener(onRealtimeSyncListener)
        super.onCleared()
    }
}
