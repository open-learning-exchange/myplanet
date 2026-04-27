package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.NetworkUtils.isNetworkConnectedFlow

@HiltViewModel
class BellDashboardViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val teamsRepository: TeamsRepository
) : ViewModel() {
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Disconnected)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _completedCourses = MutableStateFlow<List<CourseCompletion>>(emptyList())
    val completedCourses: StateFlow<List<CourseCompletion>> = _completedCourses.asStateFlow()

    init {
        viewModelScope.launch {
            isNetworkConnectedFlow.collect { isConnected ->
                if (isConnected) {
                    updateNetworkStatus(NetworkStatus.Connecting)
                } else {
                    updateNetworkStatus(NetworkStatus.Disconnected)
                }
            }
        }
    }

    fun loadCompletedCourses(userId: String) {
        viewModelScope.launch {
            _completedCourses.value = progressRepository.getCompletedCourses(userId)
        }
    }

    private fun updateNetworkStatus(status: NetworkStatus) {
        _networkStatus.value = status
    }

    suspend fun checkServerConnection(serverUrl: String): Boolean {
        val reachable = isServerReachable(serverUrl)
        updateNetworkStatus(if (reachable) NetworkStatus.Connected else NetworkStatus.Disconnected)
        return reachable
    }

    suspend fun getTeamById(teamId: String) = teamsRepository.getTeamById(teamId)
}

sealed class NetworkStatus {
    object Disconnected : NetworkStatus()
    object Connecting : NetworkStatus()
    object Connected : NetworkStatus()
}
