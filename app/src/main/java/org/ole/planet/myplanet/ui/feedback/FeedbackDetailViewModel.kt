package org.ole.planet.myplanet.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.repository.FeedbackRepository

@HiltViewModel
class FeedbackDetailViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _feedback = MutableStateFlow<RealmFeedback?>(null)
    val feedback: StateFlow<RealmFeedback?> = _feedback.asStateFlow()

    fun loadFeedback(id: String?) {
        viewModelScope.launch {
            _feedback.value = feedbackRepository.getFeedbackById(id)
        }
    }

    fun closeFeedback(id: String?) {
        viewModelScope.launch {
            feedbackRepository.closeFeedback(id)
            _feedback.value = feedbackRepository.getFeedbackById(id)
        }
    }

    fun addReply(id: String?, obj: JsonObject) {
        viewModelScope.launch {
            feedbackRepository.addReply(id, obj)
            _feedback.value = feedbackRepository.getFeedbackById(id)
        }
    }
}

