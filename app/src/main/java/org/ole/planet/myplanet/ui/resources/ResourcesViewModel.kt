package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagItem
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    // A monotonically increasing "last completed at" timestamp rather than a true/false pulse:
    // toggling a StateFlow true-then-false with no suspension in between can be conflated away
    // entirely by a collector that isn't actively resumed at that exact moment, silently
    // dropping the completion signal. A value that never repeats can't be missed that way -
    // collectors compare it against the last timestamp they handled instead of reacting to a
    // transient flip.
    private val _downloadCompletedAt = MutableStateFlow(0L)
    val downloadCompletedAt: StateFlow<Long> = _downloadCompletedAt.asStateFlow()

    private val _openedResourceIds = MutableStateFlow<Set<String>>(emptySet())
    val openedResourceIds: StateFlow<Set<String>> = _openedResourceIds.asStateFlow()

    private var observeOpenedResourcesJob: Job? = null

    fun notifyDownloadComplete() {
        _downloadCompletedAt.value = System.currentTimeMillis()
    }

    fun observeOpenedResourceIds(userId: String) {
        observeOpenedResourcesJob?.cancel()
        observeOpenedResourcesJob = viewModelScope.launch {
            resourcesRepository.observeOpenedResourceIds(userId).collectLatest { ids ->
                _openedResourceIds.value = ids
            }
        }
    }

    suspend fun addResourcesToUserLibrary(resourceIds: List<String>, userId: String): Result<Unit> {
        return resourcesRepository.addResourcesToUserLibrary(resourceIds, userId)
    }

    suspend fun getLibraryListModels(isMyCourseLib: Boolean, modelId: String?): List<ResourceListModel> = withContext(dispatcherProvider.io) {
        val enrichedLibraries = resourcesRepository.getEnrichedLibraries(isMyCourseLib, modelId)
        enrichedLibraries
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
