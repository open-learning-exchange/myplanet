package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.LocalResourceRequest
import org.ole.planet.myplanet.repository.ResourcesRepository

@HiltViewModel
class ResourcesEditorViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository
) : ViewModel() {

    private val _isTitleDuplicate = MutableStateFlow(false)
    val isTitleDuplicate: StateFlow<Boolean> = _isTitleDuplicate.asStateFlow()

    fun checkTitle(title: String) {
        viewModelScope.launch {
            _isTitleDuplicate.value = resourcesRepository.resourceTitleExists(title)
        }
    }

    fun resetTitleCheck() {
        _isTitleDuplicate.value = false
    }

    suspend fun getResourceById(resourceId: String): MyLibrary? {
        return resourcesRepository.getResourceById(resourceId)
    }

    suspend fun updateResource(resourceId: String, request: LocalResourceRequest): Result<Unit> {
        return resourcesRepository.updateLocalResource(
            resourceId = resourceId,
            title = request.title ?: "",
            author = request.author ?: "",
            year = request.year ?: "",
            description = request.description ?: "",
            publisher = request.publisher ?: "",
            linkToLicense = request.linkToLicense ?: "",
            subjects = request.subjects,
            levels = request.levels
        )
    }

    suspend fun saveResource(request: LocalResourceRequest): Result<Unit> {
        return resourcesRepository.saveLocalResource(request)
    }
}
