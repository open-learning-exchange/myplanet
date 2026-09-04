package org.ole.planet.myplanet.ui.submissions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.ole.planet.myplanet.model.QuestionAnswer
import org.ole.planet.myplanet.model.SubmissionDetail
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.TimeUtils

data class SubmissionDetailUiState(
    val title: String = "Submission Details",
    val status: String = "Status: Unknown",
    val date: String = "Date: Unknown",
    val submittedBy: String = "Submitted by: Unknown",
    val questionAnswers: List<QuestionAnswer> = emptyList()
)

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

    val uiState: StateFlow<SubmissionDetailUiState> = submissionDetailState
        .map { detail ->
            if (detail == null) {
                SubmissionDetailUiState()
            } else {
                SubmissionDetailUiState(
                    title = detail.title,
                    status = detail.status,
                    date = "Date: ${TimeUtils.getFormattedDate(detail.date)}",
                    submittedBy = detail.submittedBy,
                    questionAnswers = detail.questionAnswers
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubmissionDetailUiState())
}
