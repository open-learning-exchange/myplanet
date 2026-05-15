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

    suspend fun getLibraryListModels(isMyCourseLib: Boolean, modelId: String?): List<ResourceListModel> {
        val allLibraryItems = if (isMyCourseLib) {
            resourcesRepository.getMyLibrary(modelId)
        } else {
            resourcesRepository.getAllLibraryItems().filter {
                !it.isPrivate && it.userId?.contains(modelId) == false
            }
        }

        val allResourceIds = allLibraryItems.mapNotNull { it.resourceId ?: it.id }

        val map = HashMap(resourcesRepository.getResourceRatingsBulk(allResourceIds, modelId))
        val tagsMap = resourcesRepository.getResourceTagsBulk(allResourceIds)

        return allLibraryItems
            .sortedByDescending { it.isResourceOffline() }
            .map { library ->
                val resourceId = library.resourceId ?: library.id
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
                filename = library.filename
            )
            val rating = resourceId?.let { map[it] }
            val tags = resourceId?.let { tagsMap[it]?.map { tag -> TagItem(tag.id, tag.name) } } ?: emptyList()
            ResourceListModel(library, item, rating, tags)
        }
    }
}
