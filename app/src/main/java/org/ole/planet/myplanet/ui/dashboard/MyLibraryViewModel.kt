package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.repository.LibraryRepository
import javax.inject.Inject

data class MyLibraryUiState(
    val library: List<RealmMyLibrary> = emptyList(),
)

@HiltViewModel
class MyLibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyLibraryUiState())
    val uiState: StateFlow<MyLibraryUiState> = _uiState.asStateFlow()

    private var userContentJob: Job? = null

    fun loadLibrary(userId: String?) {
        if (userId == null) return
        userContentJob?.cancel()
        userContentJob = viewModelScope.launch {
            val myLibrary = libraryRepository.getMyLibrary(userId)
            _uiState.update { it.copy(library = myLibrary) }
        }
    }
}
