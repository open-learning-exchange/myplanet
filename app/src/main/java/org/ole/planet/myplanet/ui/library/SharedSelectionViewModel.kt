package org.ole.planet.myplanet.ui.library

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class SharedSelectionViewModel : ViewModel() {
    val selectedItems = MutableStateFlow<List<Any>>(emptyList())

    fun clearSelection() {
        selectedItems.value = emptyList()
    }

    fun setSelection(list: List<Any>) {
        selectedItems.value = list
    }
}
