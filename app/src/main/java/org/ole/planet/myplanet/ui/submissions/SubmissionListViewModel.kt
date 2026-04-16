package org.ole.planet.myplanet.ui.submissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.SubmissionItem
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import javax.inject.Inject

@HiltViewModel
class SubmissionListViewModel @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _submissions = MutableStateFlow<List<SubmissionItem>>(emptyList())
    val submissions: StateFlow<List<SubmissionItem>> = _submissions.asStateFlow()

    fun loadSubmissions(parentId: String?, userId: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            val items = submissionsRepository.getSubmissionItems(parentId, userId)
            _submissions.value = items
        }
    }
}
