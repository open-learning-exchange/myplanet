package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.model.SyncState
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val sharedPrefManager: SharedPrefManager,
    private val serverUrlMapper: ServerUrlMapper
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun startResourcesSync() {
        val isFastSync = sharedPrefManager.getFastSync()
        if (isFastSync && !sharedPrefManager.isResourcesSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(sharedPrefManager.getServerUrl())

        viewModelScope.launch {
            serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
                isServerReachable(url)
            }
            startSyncManager()
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : OnSyncListener {
            override fun onSyncStarted() {
                _syncState.value = SyncState.Syncing
            }

            override fun onSyncComplete() {
                _syncState.value = SyncState.Success
                sharedPrefManager.setResourcesSynced(true)
            }

            override fun onSyncFailed(msg: String?) {
                _syncState.value = SyncState.Failed(msg)
            }
        }, "full", listOf("resources"))
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }
}
