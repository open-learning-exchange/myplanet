package org.ole.planet.myplanet.ui.submissions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.ole.planet.myplanet.model.QuestionAnswer
import org.ole.planet.myplanet.model.SubmissionDetail
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.TimeUtils

@HiltViewModel
class SubmissionDetailViewModel @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val submissionId: String = savedStateHandle["id"] ?: ""

    private val submissionDetailState: StateFlow<SubmissionDetail?> = flow {
        val submission = submissionsRepository.getSubmissionByRemoteIdOrParentId(submissionId)
        if (submission != null) {
            val user = submission.userId?.let { userRepository.getUserById(it) }
            emit(submissionsRepository.getSubmissionDetail(submission, user))
        } else {
            emit(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val questionAnswers: StateFlow<List<QuestionAnswer>> = submissionDetailState
        .filterNotNull()
        .map { it.questionAnswers }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val title: StateFlow<String> = submissionDetailState
        .filterNotNull()
        .map { it.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Submission Details")

    val status: StateFlow<String> = submissionDetailState
        .filterNotNull()
        .map { it.status }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Status: Unknown")

    val date: StateFlow<String> = submissionDetailState
        .filterNotNull()
        .map { "Date: ${TimeUtils.getFormattedDate(it.date)}" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Date: Unknown")

    val submittedBy: StateFlow<String> = submissionDetailState
        .filterNotNull()
        .map { it.submittedBy }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Submitted by: Unknown")
}
