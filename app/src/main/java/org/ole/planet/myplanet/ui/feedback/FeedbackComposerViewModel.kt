package org.ole.planet.myplanet.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class FeedbackComposerViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    sealed interface SubmitEvent {
        object Saved : SubmitEvent
        data class Error(val message: String?) : SubmitEvent
    }

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _events = Channel<SubmitEvent>(Channel.BUFFERED)
    val events: Flow<SubmitEvent> = _events.receiveAsFlow()

    fun submitFeedback(urgent: String, type: String, message: String, item: String?, state: String?) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val user = userRepository.getUserModel()?.name ?: ""
                feedbackRepository.createAndSaveFeedback(user, urgent, type, message, item, state)
                _events.send(SubmitEvent.Saved)
            } catch (e: Exception) {
                _events.send(SubmitEvent.Error(e.message))
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
