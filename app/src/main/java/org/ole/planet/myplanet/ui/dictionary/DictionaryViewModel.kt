package org.ole.planet.myplanet.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity
import org.ole.planet.myplanet.repository.DictionaryLoad
import org.ole.planet.myplanet.repository.DictionaryRepository

sealed interface DictionaryLoadState {
    data object Idle : DictionaryLoadState
    data class Populated(val count: Long) : DictionaryLoadState
    data object FileMissing : DictionaryLoadState
    data class Failed(val cause: Throwable?) : DictionaryLoadState
}

sealed interface DictionarySearchState {
    data object Idle : DictionarySearchState
    data class Found(val entry: DictionaryEntity) : DictionarySearchState
    data object NotFound : DictionarySearchState
}

@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<DictionaryLoadState>(DictionaryLoadState.Idle)
    val loadState: StateFlow<DictionaryLoadState> = _loadState.asStateFlow()

    private val _searchState = MutableStateFlow<DictionarySearchState>(DictionarySearchState.Idle)
    val searchState: StateFlow<DictionarySearchState> = _searchState.asStateFlow()

    fun loadDictionary() {
        viewModelScope.launch {
            _loadState.value = when (val result = dictionaryRepository.insertDictionaryData()) {
                DictionaryLoad.Inserted, DictionaryLoad.AlreadyPopulated -> {
                    DictionaryLoadState.Populated(dictionaryRepository.count())
                }
                DictionaryLoad.FileMissing -> DictionaryLoadState.FileMissing
                is DictionaryLoad.Failed -> DictionaryLoadState.Failed(result.cause)
            }
        }
    }

    fun loadCount() {
        viewModelScope.launch {
            _loadState.value = DictionaryLoadState.Populated(dictionaryRepository.count())
        }
    }

    fun searchWord(word: String) {
        viewModelScope.launch {
            val entry = dictionaryRepository.findByWord(word)
            _searchState.value = if (entry != null) {
                DictionarySearchState.Found(entry)
            } else {
                DictionarySearchState.NotFound
            }
        }
    }
}
