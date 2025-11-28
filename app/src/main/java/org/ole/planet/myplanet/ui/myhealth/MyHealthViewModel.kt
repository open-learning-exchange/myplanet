package org.ole.planet.myplanet.ui.myhealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.service.SyncManager
import javax.inject.Inject

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
    data class Error(val message: String?) : SyncState()
}

@HiltViewModel
class MyHealthViewModel @Inject constructor(
    private val syncManager: SyncManager
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    fun startSync() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            syncManager.start(object : SyncListener {
                override fun onSyncStarted() {}

                override fun onSyncComplete() {
                    _syncState.value = SyncState.Success
                }

                override fun onSyncFailed(msg: String?) {
                    _syncState.value = SyncState.Error(msg)
                }
            }, "full", listOf("health"))
        }
    }
}
