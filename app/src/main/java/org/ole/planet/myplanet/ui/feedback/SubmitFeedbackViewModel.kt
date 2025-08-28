package org.ole.planet.myplanet.ui.feedback

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.FeedbackRepository

@HiltViewModel
class SubmitFeedbackViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
) : ViewModel() {

    private val _submitStatus = MutableLiveData<Result<Unit>>()
    val submitStatus: LiveData<Result<Unit>> = _submitStatus

    fun submitFeedback(
        user: String?,
        urgent: String,
        type: String,
        message: String,
        item: String? = null,
        state: String? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                feedbackRepository.submitFeedback(user, urgent, type, message, item, state)
            }.onSuccess {
                _submitStatus.postValue(Result.success(Unit))
            }.onFailure { error ->
                _submitStatus.postValue(Result.failure(error))
            }
        }
    }
}
