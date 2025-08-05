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
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.di.AppPreferences

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val databaseService: DatabaseService,
    private val libraryRepository: LibraryRepository,
    private val userRepository: UserRepository,
    private val syncManager: SyncManager,
    private val sharedPrefManager: SharedPrefManager,
    @AppPreferences private val sharedPreferences: SharedPreferences,
) : ViewModel() {

    private val serverUrlMapper = ServerUrlMapper()

    private val _resourcesState =
        MutableStateFlow<ResourcesUiState>(ResourcesUiState.Loading)
    val resourcesState: StateFlow<ResourcesUiState> = _resourcesState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val serverUrl: String
        get() = sharedPreferences.getString("serverURL", "") ?: ""

    init {
        viewModelScope.launch { loadResources() }
    }

    suspend fun loadResources() {
        try {
            _resourcesState.value = ResourcesUiState.Loading
            val user = userRepository.getCurrentUser()
            val libraryList = libraryRepository.getAllLibraryListAsync()
            val ratings = databaseService.withRealmAsync { realm ->
                RealmRating.getRatings(realm, "resource", user?.id)
            }
            _resourcesState.value = ResourcesUiState.Success(libraryList, ratings)
        } catch (e: Exception) {
            _resourcesState.value =
                ResourcesUiState.Error(e.message ?: "Unknown error")
        }
    }

    fun startResourcesSync() {
        val isFastSync = sharedPreferences.getBoolean("fastSync", false)
        if (isFastSync && !sharedPrefManager.isResourcesSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)
        viewModelScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            startSyncManager()
        }
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, sharedPreferences) { url ->
            isServerReachable(url)
        }
    }

    private suspend fun startSyncManager() {
        withContext(Dispatchers.Main) { _syncState.value = SyncState.Syncing }
        syncManager.start(
            object : SyncListener {
                override fun onSyncStarted() {}

                override fun onSyncComplete() {
                    viewModelScope.launch {
                        _syncState.value = SyncState.Success
                        sharedPrefManager.setResourcesSynced(true)
                        loadResources()
                    }
                }

                override fun onSyncFailed(msg: String?) {
                    viewModelScope.launch {
                        _syncState.value =
                            SyncState.Error(msg ?: "Unknown error")
                    }
                }
            },
            "full",
            listOf("resources")
        )
    }

    sealed class ResourcesUiState {
        object Loading : ResourcesUiState()
        data class Success(
            val resources: List<RealmMyLibrary>,
            val ratingMap: HashMap<String?, com.google.gson.JsonObject>
        ) : ResourcesUiState()
        data class Error(val message: String) : ResourcesUiState()
    }

    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        object Success : SyncState()
        data class Error(val message: String) : SyncState()
    }
}

