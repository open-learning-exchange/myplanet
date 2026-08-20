package org.ole.planet.myplanet.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.services.UserSessionManager

@HiltViewModel
class FeedbackComposerViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    sealed class SubmitState {
        object Idle : SubmitState()
        object Submitting : SubmitState()
        object Saved : SubmitState()
        data class Error(val message: String) : SubmitState()
    }

    private val _uiState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val uiState: StateFlow<SubmitState> = _uiState.asStateFlow()

    fun submitFeedback(urgent: String, type: String, message: String, item: String?, state: String?) {
        viewModelScope.launch {
            _uiState.value = SubmitState.Submitting
            try {
                val user = userSessionManager.getUserModel()?.name ?: ""
                feedbackRepository.createAndSaveFeedback(user, urgent, type, message, item, state)
                _uiState.value = SubmitState.Saved
            } catch (e: Exception) {
                _uiState.value = SubmitState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
