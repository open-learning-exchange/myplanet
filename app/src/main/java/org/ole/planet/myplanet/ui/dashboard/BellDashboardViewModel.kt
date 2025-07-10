package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.NetworkRepository
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow

data class BellDashboardUiState(
    val networkStatus: NetworkStatus = NetworkStatus.Disconnected
)

class BellDashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BellDashboardUiState())
    val uiState: StateFlow<BellDashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            isNetworkConnectedFlow.collect { isConnected ->
                updateNetworkStatus(isConnected)
            }
        }
    }

    private fun updateNetworkStatus(isConnected: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                networkStatus = if (!isConnected) NetworkStatus.Disconnected else NetworkStatus.Connecting
            )
        }
    }

    suspend fun checkServerConnection(serverUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            NetworkRepository.isServerReachable(serverUrl)
        }
    }
}

sealed class NetworkStatus {
    object Disconnected : NetworkStatus()
    object Connecting : NetworkStatus()
    object Connected : NetworkStatus()
}
