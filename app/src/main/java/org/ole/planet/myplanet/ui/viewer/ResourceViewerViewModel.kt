package org.ole.planet.myplanet.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.domain.usecase.UpdateViewerSessionUseCase
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ResourcesRepository

@HiltViewModel
class ResourceViewerViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val updateViewerSessionUseCase: UpdateViewerSessionUseCase
) : ViewModel() {

    private val _viewerSessionState = MutableSharedFlow<UpdateViewerSessionUseCase.ViewerSessionResult>()
    val viewerSessionState = _viewerSessionState.asSharedFlow()

    private var authSessionJob: Job? = null

    fun startAuthSession() {
        authSessionJob?.cancel()
        authSessionJob = viewModelScope.launch {
            updateViewerSessionUseCase().collect { result ->
                _viewerSessionState.emit(result)
            }
        }
    }

    fun stopAuthSession() {
        authSessionJob?.cancel()
        authSessionJob = null
    }

    suspend fun getLibraryItemById(id: String): MyLibrary? {
        return resourcesRepository.getLibraryItemById(id)
    }

    suspend fun updateLibraryItemTranslationAudioPath(id: String, outputFile: String?) {
        resourcesRepository.updateLibraryItem(id) { it.translationAudioPath = outputFile }
    }
}
