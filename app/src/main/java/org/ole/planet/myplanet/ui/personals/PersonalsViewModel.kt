package org.ole.planet.myplanet.ui.personals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.repository.PersonalUpdate
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.services.UserSessionManager

sealed class UploadState {
    object Idle : UploadState()
    object Loading : UploadState()
    data class Success(val message: String) : UploadState()
    data class Error(val message: String) : UploadState()
}

@HiltViewModel
class PersonalsViewModel @Inject constructor(
    private val personalsRepository: PersonalsRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    val personals: StateFlow<List<Personal>> = flow {
        val user = userSessionManager.getUserModel()
        emitAll(personalsRepository.getPersonalResources(user?.id))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    fun uploadPersonal(personal: Personal) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            try {
                val result = personalsRepository.uploadPersonal(personal)
                _uploadState.value = UploadState.Success(result)
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(e.message ?: "Upload failed")
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }

    fun updatePersonalResource(id: String, update: PersonalUpdate) {
        viewModelScope.launch {
            personalsRepository.updatePersonalResource(id, update)
        }
    }

    fun deletePersonalResource(id: String) {
        viewModelScope.launch {
            personalsRepository.deletePersonalResource(id)
        }
    }
}
