package org.ole.planet.myplanet.ui.components

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SelectionViewModel : ViewModel() {
    private val _selectedItems = MutableStateFlow<Set<Any>>(emptySet())
    val selectedItems: StateFlow<Set<Any>> = _selectedItems.asStateFlow()

    fun toggleSelection(item: Any) {
        _selectedItems.update { currentSet ->
            if (currentSet.contains(item)) {
                currentSet - item
            } else {
                currentSet + item
            }
        }
    }

    fun selectAll(items: List<Any>) {
        _selectedItems.value = items.toSet()
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
    }

    fun setSelectedItems(items: List<Any>) {
        _selectedItems.value = items.toSet()
    }
}
