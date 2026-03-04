package org.ole.planet.myplanet.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class FeedbackDetailViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _feedback = MutableStateFlow<RealmFeedback?>(null)
    val feedback: StateFlow<RealmFeedback?> = _feedback.asStateFlow()

    private val _events = MutableSharedFlow<FeedbackDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FeedbackDetailEvent> = _events.asSharedFlow()

    fun loadFeedback(id: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            _feedback.value = feedbackRepository.getFeedbackById(id)
        }
    }

    fun closeFeedback(id: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            feedbackRepository.closeFeedback(id)
            _feedback.value = feedbackRepository.getFeedbackById(id)
            _events.emit(FeedbackDetailEvent.CloseFeedbackSuccess)
        }
    }

    fun addReply(id: String?, obj: JsonObject) {
        viewModelScope.launch(dispatcherProvider.io) {
            feedbackRepository.addReply(id, obj)
            _feedback.value = feedbackRepository.getFeedbackById(id)
        }
    }

    sealed class FeedbackDetailEvent {
        object CloseFeedbackSuccess : FeedbackDetailEvent()
    }
}
