package org.ole.planet.myplanet.ui.health

import org.ole.planet.myplanet.utils.Utilities
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
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.HealthRepository

@HiltViewModel
class AddExaminationViewModel @Inject constructor(
    private val healthRepository: HealthRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableSharedFlow<Boolean>()
    val saveResult: SharedFlow<Boolean> = _saveResult.asSharedFlow()

    fun saveExamination(examination: RealmHealthExamination?, pojo: RealmHealthExamination?, user: RealmUser?) {
        if (_isSaving.value) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                healthRepository.saveExamination(examination, pojo, user)
                _saveResult.emit(true)
            } catch (e: Exception) {
                Utilities.logException(e, "AddExaminationViewModel")
                _saveResult.emit(false)
            } finally {
                _isSaving.value = false
            }
        }
    }
}
