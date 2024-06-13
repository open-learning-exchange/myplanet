package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow

class BellDashboardViewModel : ViewModel() {
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Disconnected)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    init {
        viewModelScope.launch {
            isNetworkConnectedFlow.collect { isConnected ->
                updateNetworkStatus(isConnected)
            }
        }
    }

    private fun updateNetworkStatus(isConnected: Boolean) {
        viewModelScope.launch {
            _networkStatus.value = when {
                !isConnected -> NetworkStatus.Disconnected
                else -> NetworkStatus.Connecting
            }
        }
    }

    suspend fun checkServerConnection(serverUrl: String?): Boolean {
        return withContext(Dispatchers.IO) {
            isServerReachable(serverUrl)
        }
    }
}

sealed class NetworkStatus {
    object Disconnected : NetworkStatus()
    object Connecting : NetworkStatus()
    object Connected : NetworkStatus()
}
