package org.ole.planet.myplanet.ui.library

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SelectionViewModel<T> : ViewModel() {
    private val _selectedItems = MutableStateFlow<List<T>>(emptyList())
    val selectedItems: StateFlow<List<T>> = _selectedItems.asStateFlow()

    fun setSelectedItems(items: List<T>) {
        _selectedItems.value = items
    }

    fun clearSelection() {
        _selectedItems.value = emptyList()
    }
}
