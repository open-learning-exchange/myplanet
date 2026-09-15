package org.ole.planet.myplanet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.OfflineResourceItem
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

data class StorageCategoryUiState(
    val items: List<OfflineResourceItem> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val isEmpty: Boolean = false
) {
    val checkedCount: Int get() = items.count { it.isChecked }
    val allChecked: Boolean get() = checkedCount == items.size && items.isNotEmpty()
}

@HiltViewModel
class StorageCategoryViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageCategoryUiState())
    val uiState: StateFlow<StorageCategoryUiState> = _uiState.asStateFlow()

    private val _deleteCompleteEvent = Channel<Unit>(Channel.BUFFERED)
    val deleteCompleteEvent: Flow<Unit> = _deleteCompleteEvent.receiveAsFlow()

    private var hasLoaded = false

    fun loadResources(olePath: String, extensions: Set<String>, allKnownExtensions: Set<String>) {
        if (hasLoaded) return
        hasLoaded = true
        _uiState.update { it.copy(isLoading = true, isEmpty = false) }
        viewModelScope.launch(dispatcherProvider.io) {
            val loaded = resourcesRepository.getOfflineResourceItems(olePath, extensions, allKnownExtensions)
            _uiState.update {
                it.copy(
                    items = loaded,
                    isLoading = false,
                    isEmpty = loaded.isEmpty()
                )
            }
        }
    }

    fun toggleItemChecked(resourceId: String) {
        _uiState.update { state ->
            val updatedItems = state.items.map {
                if (it.resourceId == resourceId) it.copy(isChecked = !it.isChecked) else it
            }
            state.copy(items = updatedItems)
        }
    }

    fun toggleAllChecked() {
        _uiState.update { state ->
            val allChecked = state.items.all { it.isChecked }
            val updatedItems = state.items.map { it.copy(isChecked = !allChecked) }
            state.copy(items = updatedItems)
        }
    }

    fun deleteSelected(olePath: String) {
        val selected = _uiState.value.items.filter { it.isChecked }
        if (selected.isEmpty()) return
        deleteItems(olePath, selected)
    }

    fun deleteAll(olePath: String) {
        val items = _uiState.value.items
        if (items.isEmpty()) return
        deleteItems(olePath, items)
    }

    private fun deleteItems(olePath: String, items: List<OfflineResourceItem>) {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch(dispatcherProvider.io) {
            resourcesRepository.deleteOfflineResources(olePath, items)
            _uiState.update { it.copy(isDeleting = false) }
            _deleteCompleteEvent.send(Unit)
        }
    }
}
