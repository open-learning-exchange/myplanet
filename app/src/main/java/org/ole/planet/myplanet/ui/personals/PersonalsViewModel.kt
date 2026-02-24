package org.ole.planet.myplanet.ui.personals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import javax.inject.Inject

@HiltViewModel
class PersonalsViewModel @Inject constructor(
    private val personalsRepository: PersonalsRepository,
    private val userSessionManager: UserSessionManager,
    private val uploadManager: UploadManager
) : ViewModel() {

    private val _personals = MutableStateFlow<List<RealmMyPersonal>>(emptyList())
    val personals: StateFlow<List<RealmMyPersonal>> = _personals.asStateFlow()

    init {
        getPersonals()
    }

    private fun getPersonals() {
        viewModelScope.launch {
            val user = userSessionManager.getUserModel()
            personalsRepository.getPersonalResources(user?.id).collectLatest {
                _personals.value = it
            }
        }
    }

    suspend fun uploadPersonal(personal: RealmMyPersonal): String {
        return uploadManager.uploadMyPersonal(personal)
    }

    fun updatePersonal(id: String, title: String, description: String) {
        viewModelScope.launch {
            personalsRepository.updatePersonalResource(id) { realmPersonal ->
                realmPersonal.description = description
                realmPersonal.title = title
            }
        }
    }

    fun deletePersonal(id: String) {
        viewModelScope.launch {
            personalsRepository.deletePersonalResource(id)
        }
    }
}
