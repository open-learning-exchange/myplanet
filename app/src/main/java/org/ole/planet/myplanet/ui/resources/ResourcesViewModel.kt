package org.ole.planet.myplanet.ui.resources

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.di.LibraryRepository
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.utilities.ServerUrlMapper

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val serverUrlMapper = ServerUrlMapper()

    private val _libraryItems = MutableStateFlow<List<RealmMyLibrary>>(emptyList())
    val libraryItems: StateFlow<List<RealmMyLibrary>> = _libraryItems.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    init {
        loadResources()
    }

    fun loadResources() {
        _libraryItems.value = libraryRepository.getAllLibraryItems()
    }

    fun startResourcesSync(settings: SharedPreferences, serverUrl: String) {
        val mapping = serverUrlMapper.processUrl(serverUrl)
        viewModelScope.launch(Dispatchers.IO) {
            serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
                isServerReachable(url)
            }
            withContext(Dispatchers.Main) { startSyncManager() }
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() {
                _syncStatus.value = SyncStatus.InProgress
            }

            override fun onSyncComplete() {
                viewModelScope.launch(Dispatchers.Main) {
                    _syncStatus.value = SyncStatus.Completed
                    loadResources()
                }
            }

            override fun onSyncFailed(msg: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    _syncStatus.value = SyncStatus.Failed(msg)
                }
            }
        }, "full", listOf("resources"))
    }
}

sealed class SyncStatus {
    object Idle : SyncStatus()
    object InProgress : SyncStatus()
    object Completed : SyncStatus()
    data class Failed(val message: String?) : SyncStatus()
}
