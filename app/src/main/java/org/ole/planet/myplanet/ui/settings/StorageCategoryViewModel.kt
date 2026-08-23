package org.ole.planet.myplanet.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import org.ole.planet.myplanet.utils.FileUtils
import javax.inject.Inject

data class StorageCategoryUiState(
    val items: List<OfflineResourceItem> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val isEmpty: Boolean = false
)

@HiltViewModel
class StorageCategoryViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageCategoryUiState())
    val uiState: StateFlow<StorageCategoryUiState> = _uiState.asStateFlow()

    private val _deleteCompleteEvent = Channel<Unit>(Channel.BUFFERED)
    val deleteCompleteEvent: Flow<Unit> = _deleteCompleteEvent.receiveAsFlow()

    private var hasLoaded = false

    fun loadResources(extensions: Set<String>, allKnownExtensions: Set<String>) {
        if (hasLoaded) return
        hasLoaded = true
        _uiState.update { it.copy(isLoading = true, isEmpty = false) }
        viewModelScope.launch(dispatcherProvider.io) {
            val olePath = FileUtils.getOlePath(context)
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

    fun deleteItems(items: List<OfflineResourceItem>) {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch(dispatcherProvider.io) {
            val olePath = FileUtils.getOlePath(context)
            resourcesRepository.deleteOfflineResources(olePath, items)
            _uiState.update { it.copy(isDeleting = false) }
            _deleteCompleteEvent.send(Unit)
        }
    }
}
