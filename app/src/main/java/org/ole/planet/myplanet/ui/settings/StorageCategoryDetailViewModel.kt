package org.ole.planet.myplanet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.OfflineResourceItem
import org.ole.planet.myplanet.repository.ResourcesRepository

@HiltViewModel
class StorageCategoryDetailViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<OfflineResourceItem>?>(null)
    val items: StateFlow<List<OfflineResourceItem>?> = _items.asStateFlow()

    fun loadResources(olePath: String, extensions: Set<String>, allKnownExtensions: Set<String>) {
        if (_items.value == null) {
            viewModelScope.launch {
                val loaded = resourcesRepository.getOfflineResourceItems(olePath, extensions, allKnownExtensions)
                _items.value = loaded
            }
        }
    }

    fun deleteResources(olePath: String, toDelete: List<OfflineResourceItem>, onComplete: () -> Unit) {
        viewModelScope.launch {
            resourcesRepository.deleteOfflineResources(olePath, toDelete)
            val currentList = _items.value ?: emptyList()
            _items.value = currentList.filterNot { item -> toDelete.any { it.resourceId == item.resourceId } }
            onComplete()
        }
    }
}
