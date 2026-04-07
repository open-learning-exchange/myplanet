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
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class AddExaminationViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
    private val dispatcherProvider: DispatcherProvider
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
