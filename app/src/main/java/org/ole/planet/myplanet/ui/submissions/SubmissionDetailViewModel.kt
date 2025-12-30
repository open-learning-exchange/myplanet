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
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.utilities.TimeUtils

@HiltViewModel
class SubmissionDetailViewModel @Inject constructor(
    private val submissionRepository: SubmissionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val submissionId: String = savedStateHandle["id"] ?: ""

    private val submissionDetailState: StateFlow<SubmissionDetail?> = flow {
        emit(submissionRepository.getSubmissionDetail(submissionId))
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val questionAnswers: StateFlow<List<QuestionAnswer>> = submissionDetailState
        .filterNotNull()
        .map { it.questionAnswers }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val title: StateFlow<String> = submissionDetailState
        .filterNotNull()
        .map { it.title }
        .stateIn(viewModelScope, SharingStarted.Lazily, "Submission Details")

    val status: StateFlow<String> = submissionDetailState
        .filterNotNull()
        .map { it.status }
        .stateIn(viewModelScope, SharingStarted.Lazily, "Status: Unknown")

    val date: StateFlow<String> = submissionDetailState
        .filterNotNull()
        .map { "Date: ${TimeUtils.getFormattedDate(it.date)}" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "Date: Unknown")

    val submittedBy: StateFlow<String> = submissionDetailState
        .filterNotNull()
        .map { it.submittedBy }
        .stateIn(viewModelScope, SharingStarted.Lazily, "Submitted by: Unknown")
}
