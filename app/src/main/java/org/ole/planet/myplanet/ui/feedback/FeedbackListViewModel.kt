package org.ole.planet.myplanet.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.FeedbackRepository

@HiltViewModel
class FeedbackListViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _feedbackList = MutableStateFlow<List<RealmFeedback>>(emptyList())
    val feedbackList: StateFlow<List<RealmFeedback>> = _feedbackList.asStateFlow()

    fun loadFeedback(userModel: RealmUserModel?) {
        viewModelScope.launch {
            feedbackRepository.getFeedback(userModel).collect { feedbacks ->
                _feedbackList.value = feedbacks
            }
        }
    }
}

