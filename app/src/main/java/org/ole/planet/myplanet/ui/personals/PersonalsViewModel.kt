package org.ole.planet.myplanet.ui.personals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.services.UserSessionManager

@HiltViewModel
class PersonalsViewModel @Inject constructor(
    private val personalsRepository: PersonalsRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    val personals: StateFlow<List<RealmMyPersonal>> = flow {
        val user = userSessionManager.getUserModel()
        emitAll(personalsRepository.getPersonalResources(user?.id))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updatePersonalResource(id: String, updater: (RealmMyPersonal) -> Unit) {
        viewModelScope.launch {
            personalsRepository.updatePersonalResource(id, updater)
        }
    }

    fun deletePersonalResource(id: String) {
        viewModelScope.launch {
            personalsRepository.deletePersonalResource(id)
        }
    }
}
