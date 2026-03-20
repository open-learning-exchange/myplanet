package org.ole.planet.myplanet.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.services.UserSessionManager

@HiltViewModel
class FeedbackListViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _feedbackList = MutableStateFlow<List<RealmFeedback>>(emptyList())
    val feedbackList: StateFlow<List<RealmFeedback>> = _feedbackList.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadFeedback()
    }

    private fun loadFeedback() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val user = userSessionManager.getUserModel()
                feedbackRepository.getFeedback(user).collectLatest { feedback ->
                    _feedbackList.value = feedback
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun refreshFeedback() {
        loadFeedback()
    }
}
