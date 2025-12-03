package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.TagRepository
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    fun getTags(dbType: String?) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val tags = tagRepository.getTags(dbType)
                val childMap = tagRepository.buildChildMap()
                _uiState.value = UiState.Success(tags, childMap)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message)
            }
        }
    }
}

sealed class UiState {
    object Loading : UiState()
    data class Success(val tags: List<RealmTag>, val childMap: HashMap<String, List<RealmTag>>) : UiState()
    data class Error(val message: String?) : UiState()
}
