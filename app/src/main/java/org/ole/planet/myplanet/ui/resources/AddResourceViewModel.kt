package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.PersonalsRepository
import javax.inject.Inject

@HiltViewModel
class AddResourceViewModel @Inject constructor(
    private val personalsRepository: PersonalsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<AddResourceState>(AddResourceState.Idle)
    val state: StateFlow<AddResourceState> = _state.asStateFlow()

    fun checkTitleExists(title: String, userId: String?) {
        viewModelScope.launch {
            if (personalsRepository.personalTitleExists(title, userId)) {
                _state.value = AddResourceState.TitleExists
            }
        }
    }

    fun saveResource(title: String, userId: String?, userName: String?, path: String?, desc: String) {
        viewModelScope.launch {
            if (personalsRepository.personalTitleExists(title, userId)) {
                _state.value = AddResourceState.TitleExists
            } else {
                personalsRepository.savePersonalResource(title, userId, userName, path, desc)
                _state.value = AddResourceState.Success
            }
        }
    }

    fun resetState() {
        _state.value = AddResourceState.Idle
    }
}

sealed class AddResourceState {
    object Idle : AddResourceState()
    object TitleExists : AddResourceState()
    object Success : AddResourceState()
}
