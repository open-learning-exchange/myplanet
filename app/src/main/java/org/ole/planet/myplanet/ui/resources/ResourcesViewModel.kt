package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.SyncState
import org.ole.planet.myplanet.model.TagItem
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val sharedPrefManager: SharedPrefManager,
    private val serverUrlMapper: ServerUrlMapper,
    private val resourcesRepository: ResourcesRepository
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val _downloadComplete = MutableStateFlow(false)
    val downloadComplete: StateFlow<Boolean> = _downloadComplete.asStateFlow()

    private val _openedResourceIds = MutableStateFlow<Set<String>>(emptySet())
    val openedResourceIds: StateFlow<Set<String>> = _openedResourceIds.asStateFlow()

    private var observeOpenedResourcesJob: Job? = null

    fun notifyDownloadComplete() {
        _downloadComplete.value = true
        _downloadComplete.value = false
    }

    fun observeOpenedResourceIds(userId: String) {
        observeOpenedResourcesJob?.cancel()
        observeOpenedResourcesJob = viewModelScope.launch {
            resourcesRepository.observeOpenedResourceIds(userId).collectLatest { ids ->
                _openedResourceIds.value = ids
            }
        }
    }

    fun startResourcesSync() {
        val isFastSync = sharedPrefManager.getFastSync()
        if (isFastSync && !sharedPrefManager.isSynced(SharedPrefManager.SyncKey.RESOURCES)) {
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
                sharedPrefManager.setSynced(SharedPrefManager.SyncKey.RESOURCES, true)
            }

            override fun onSyncFailed(msg: String?) {
                _syncState.value = SyncState.Failed(msg)
            }
        }, "full", listOf("resources"))
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    suspend fun addResourcesToUserLibrary(resourceIds: List<String>, userId: String): Result<Unit> {
        return resourcesRepository.addResourcesToUserLibrary(resourceIds, userId)
    }

    suspend fun getLibraryListModels(isMyCourseLib: Boolean, modelId: String?): List<ResourceListModel> {
        val enrichedLibraries = resourcesRepository.getEnrichedLibraries(isMyCourseLib, modelId)
        return enrichedLibraries
            .sortedByDescending { (library, _, _) -> library.isResourceOffline() }
            .map { (library, rating, libraryTags) ->
            val item = ResourceItem(
                id = library.id,
                title = library.title,
                description = library.description,
                createdDate = library.createdDate,
                averageRating = library.averageRating,
                timesRated = library.timesRated,
                resourceId = library.resourceId,
                isOffline = library.isResourceOffline(),
                _rev = library._rev,
                uploadDate = library.uploadDate,
                filename = library.filename,
                resourceLocalAddress = library.resourceLocalAddress
            )
            val tags = libraryTags.map { tag -> TagItem(tag.id, tag.name) }
            ResourceListModel(library, item, rating, tags)
        }
    }
}
