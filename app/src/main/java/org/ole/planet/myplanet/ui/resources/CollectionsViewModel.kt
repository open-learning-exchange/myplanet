package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

sealed class CollectionsState {
    object Idle : CollectionsState()
    object Loading : CollectionsState()
    data class Success(
        val list: List<TagEntity>,
        val childMap: Map<String, List<TagEntity>>
    ) : CollectionsState()
    object Empty : CollectionsState()
    data class Error(val message: String) : CollectionsState()
}

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val tagsRepository: TagsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _state = MutableStateFlow<CollectionsState>(CollectionsState.Idle)
    val state: StateFlow<CollectionsState> = _state.asStateFlow()

    private var currentDbType: String? = null

    fun loadTags(dbType: String?) {
        if (dbType == currentDbType && _state.value !is CollectionsState.Idle && _state.value !is CollectionsState.Error) {
            return
        }

        currentDbType = dbType
        _state.value = CollectionsState.Loading

        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val tagsWithChildren = tagsRepository.getTagsWithChildren(dbType)
                val list = tagsWithChildren.keys.toList()
                val childMap = LinkedHashMap<String, List<TagEntity>>()
                for ((key, value) in tagsWithChildren) {
                    if (value.isNotEmpty()) {
                        childMap[key.id ?: ""] = value
                    }
                }

                if (list.isEmpty() && childMap.isEmpty()) {
                    _state.value = CollectionsState.Empty
                } else {
                    _state.value = CollectionsState.Success(list, childMap)
                }
            } catch (e: Exception) {
                _state.value = CollectionsState.Error(e.message ?: "An error occurred")
            }
        }
    }
}
