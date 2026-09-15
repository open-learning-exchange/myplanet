package org.ole.planet.myplanet.ui.submissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubmissionViewModel @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _type = MutableStateFlow("")
    private val _query = MutableStateFlow("")

    private val userIdFlow = flow { emit(userRepository.getActiveUserIdSuspending()) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private val allSubmissionsFlow = userIdFlow.flatMapLatest { uid ->
        submissionsRepository.getSubmissionsFlow(uid)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private val exams: StateFlow<HashMap<String?, StepExam>> = allSubmissionsFlow.mapLatest { subs ->
        HashMap(submissionsRepository.getExamMap(subs))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), hashMapOf())

    private val filteredProjections = combine(allSubmissionsFlow, _type, _query, exams, userIdFlow) { subs, type, query, examMap, uid ->
        submissionsRepository.getSubmissionProjections(subs, uid, type, query, examMap)
    }.flowOn(dispatcherProvider.io).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val submissions: StateFlow<List<SubmissionUiModel>> = combine(filteredProjections, exams) { projections, examsMap ->
        projections.map { projection ->
            val examTitle = examsMap[projection.submission.parentId]?.name ?: "Submissions"
            SubmissionUiModel(
                id = projection.submission.id,
                status = projection.submission.status,
                startTime = projection.submission.startTime,
                lastUpdateTime = projection.submission.lastUpdateTime,
                parentId = projection.submission.parentId,
                userId = projection.submission.userId,
                submitterName = projection.submitterName,
                examTitle = examTitle,
                submissionCount = projection.submissionCount
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(type: String, query: String) {
        _type.value = type
        _query.value = query
    }
}
