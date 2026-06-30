package org.ole.planet.myplanet.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.decrypt
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

data class AddExaminationState(
    val isLoading: Boolean = true,
    val user: RealmUser? = null,
    val pojo: RealmHealthExamination? = null,
    val health: RealmMyHealth? = null,
    val examination: RealmHealthExamination? = null
)

@HiltViewModel
class AddExaminationViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _state = MutableStateFlow(AddExaminationState())
    val state: StateFlow<AddExaminationState> = _state.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableSharedFlow<Boolean>()
    val saveResult: SharedFlow<Boolean> = _saveResult.asSharedFlow()

    fun loadData(userId: String?, examinationId: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            var user: RealmUser? = null
            var pojo: RealmHealthExamination? = null
            var health: RealmMyHealth? = null
            var examination: RealmHealthExamination? = null

            withContext(dispatcherProvider.io) {
                if (userId != null) {
                    val (u, p) = healthRepository.getHealthEntry(userId)
                    user = u
                    pojo = p

                    val updatedUser = userRepository.ensureUserSecurityKeys(userId)
                    if (updatedUser != null) {
                        user = updatedUser
                    }
                }

                if (pojo != null && pojo.data?.isNotEmpty() == true) {
                    try {
                        health = JsonUtils.gson.fromJson(decrypt(pojo.data, user?.key, user?.iv), RealmMyHealth::class.java)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (health == null) {
                    health = healthRepository.initHealth()
                }

                if (examinationId != null) {
                    examination = healthRepository.getExaminationById(examinationId)
                }
            }

            _state.value = AddExaminationState(
                isLoading = false,
                user = user,
                pojo = pojo,
                health = health,
                examination = examination
            )
        }
    }

    fun saveExamination(examination: RealmHealthExamination?, pojo: RealmHealthExamination?, user: RealmUser?) {
        if (_isSaving.value) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                withContext(dispatcherProvider.io) {
                    healthRepository.saveExamination(examination, pojo, user)
                }
                _saveResult.emit(true)
            } catch (e: Exception) {
                e.printStackTrace()
                _saveResult.emit(false)
            } finally {
                _isSaving.value = false
            }
        }
    }
}
