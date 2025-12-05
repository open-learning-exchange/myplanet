package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.TagRepository

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val tagRepository: TagRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<CollectionsUiState>(CollectionsUiState.Loading)
    val uiState: StateFlow<CollectionsUiState> = _uiState

    fun fetchTags(dbType: String?) {
        if (uiState.value is CollectionsUiState.Success) return

        viewModelScope.launch {
            _uiState.value = CollectionsUiState.Loading
            try {
                val tags = withContext(Dispatchers.IO) {
                    tagRepository.getTags(dbType)
                }
                val childMap = withContext(Dispatchers.IO) {
                    tagRepository.buildChildMap()
                }
                _uiState.value = CollectionsUiState.Success(tags, childMap)
            } catch (e: Exception) {
                _uiState.value = CollectionsUiState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }
}

sealed class CollectionsUiState {
    object Loading : CollectionsUiState()
    data class Success(val tags: List<RealmTag>, val childMap: HashMap<String, List<RealmTag>>) : CollectionsUiState()
    data class Error(val message: String) : CollectionsUiState()
}
