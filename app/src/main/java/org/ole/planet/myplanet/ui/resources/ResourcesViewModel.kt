package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val userDeferred: Deferred<UserEntity?> = viewModelScope.async {
        userRepository.getUserModel().also {
            _currentUser.value = it
        }
    }

    suspend fun getCurrentUser(): UserEntity? {
        return userDeferred.await()
    }

    enum class SortMode { NONE, DATE, TITLE }
    private var sortMode = SortMode.NONE
    private var isAscending = true
    private var isTitleAscending = false

    private val _downloadComplete = MutableStateFlow(false)
    val downloadComplete: StateFlow<Boolean> = _downloadComplete.asStateFlow()

    private val _openedResourceIds = MutableStateFlow<Set<String>>(emptySet())
    val openedResourceIds: StateFlow<Set<String>> = _openedResourceIds.asStateFlow()

    private var observeOpenedResourcesJob: Job? = null
    private var currentObservedUserId: String? = null

    fun notifyDownloadComplete() {
        _downloadComplete.value = true
        _downloadComplete.value = false
    }

    fun observeOpenedResourceIds(userId: String) {
        if (userId.isNotEmpty() && userId == currentObservedUserId && observeOpenedResourcesJob?.isActive == true) {
            return
        }
        currentObservedUserId = userId
        observeOpenedResourcesJob?.cancel()
        observeOpenedResourcesJob = viewModelScope.launch {
            resourcesRepository.observeOpenedResourceIds(userId).collectLatest { ids ->
                _openedResourceIds.value = ids
            }
        }
    }

    suspend fun saveSearchActivity(userName: String, searchText: String, planetCode: String, parentCode: String, searchTags: List<TagEntity>, subjects: Set<String>, languages: Set<String>, levels: Set<String>, mediums: Set<String>) = withContext(dispatcherProvider.io) {
        resourcesRepository.saveSearchActivity(userName, searchText, planetCode, parentCode, searchTags, subjects, languages, levels, mediums)
    }

    suspend fun removeResourcesFromShelf(resourceIds: List<String>, userId: String): Result<Unit> = withContext(dispatcherProvider.io) {
        resourcesRepository.removeResourcesFromShelf(resourceIds, userId)
    }

    suspend fun getFilterFacets(libraries: List<MyLibrary>): Map<String, Set<String>> = withContext(dispatcherProvider.default) {
        resourcesRepository.getFilterFacets(libraries)
    }

    suspend fun addResourcesToUserLibrary(resourceIds: List<String>, userId: String): Result<Unit> {
        return resourcesRepository.addResourcesToUserLibrary(resourceIds, userId)
    }

    suspend fun getLibraryListModels(isMyCourseLib: Boolean, modelId: String?): List<ResourceListModel> = withContext(dispatcherProvider.io) {
        applyCurrentSort(resourcesRepository.getResourceListModels(isMyCourseLib, modelId))
    }

    suspend fun toggleSortOrder(list: List<ResourceListModel>): List<ResourceListModel> = withContext(dispatcherProvider.io) {
        sortMode = SortMode.DATE
        isAscending = !isAscending
        applyCurrentSort(list)
    }

    suspend fun toggleTitleSortOrder(list: List<ResourceListModel>): List<ResourceListModel> = withContext(dispatcherProvider.io) {
        sortMode = SortMode.TITLE
        isTitleAscending = !isTitleAscending
        applyCurrentSort(list)
    }

    suspend fun applyCurrentSort(list: List<ResourceListModel>): List<ResourceListModel> = withContext(dispatcherProvider.io) {
        when (sortMode) {
            SortMode.DATE -> {
                if (isAscending) list.sortedBy { it.item.createdDate }
                else list.sortedByDescending { it.item.createdDate }
            }
            SortMode.TITLE -> {
                val withKeys = list.map { it to (it.item.title?.lowercase(Locale.ROOT) ?: "") }
                if (isTitleAscending) withKeys.sortedBy { it.second }.map { it.first }
                else withKeys.sortedByDescending { it.second }.map { it.first }
            }
            SortMode.NONE -> list
        }
    }
}
