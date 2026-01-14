package org.ole.planet.myplanet.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.service.UserSessionManager
import javax.inject.Inject

@HiltViewModel
class FeedbackListViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _feedbackList = MutableStateFlow<List<RealmFeedback>>(emptyList())
    val feedbackList: StateFlow<List<RealmFeedback>> = _feedbackList.asStateFlow()

    init {
        loadFeedback()
    }

    private fun loadFeedback() {
        viewModelScope.launch {
            val user = userSessionManager.userModel
            feedbackRepository.getFeedback(user).collectLatest { feedback ->
                _feedbackList.value = feedback
            }
        }
    }
    fun refreshFeedback() {
        loadFeedback()
    }
}
