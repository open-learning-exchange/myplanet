package org.ole.planet.myplanet.ui.submissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.SubmissionItem
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class SubmissionListViewModel @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _submissions = MutableStateFlow<List<SubmissionItem>>(emptyList())
    val submissions: StateFlow<List<SubmissionItem>> = _submissions.asStateFlow()

    private val _exportProgress = MutableStateFlow(false)
    val exportProgress: StateFlow<Boolean> = _exportProgress.asStateFlow()

    private val _exportFile = MutableSharedFlow<File?>()
    val exportFile: SharedFlow<File?> = _exportFile.asSharedFlow()

    fun generateSubmissionPdf(submissionId: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            _exportProgress.value = true
            val file = submissionsRepository.generateSubmissionPdf(submissionId)
            _exportFile.emit(file)
            _exportProgress.value = false
        }
    }

    fun generateMultipleSubmissionsPdf(submissionIds: List<String>, examTitle: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            _exportProgress.value = true
            val file = submissionsRepository.generateMultipleSubmissionsPdf(submissionIds, examTitle)
            _exportFile.emit(file)
            _exportProgress.value = false
        }
    }

    fun loadSubmissions(parentId: String?, userId: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            val items = submissionsRepository.getSubmissionItems(parentId, userId)
            _submissions.value = items
        }
    }
}
