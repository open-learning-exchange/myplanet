package org.ole.planet.myplanet.ui.submissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubmissionViewModel @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val surveysRepository: SurveysRepository,
    private val userRepository: UserRepository,
    dispatcherProvider: DispatcherProvider,
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

    private val assignedSurveysFlow = userIdFlow.mapLatest { uid ->
        surveysRepository.getAssignedSurveys(uid)
    }.flowOn(dispatcherProvider.io).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private val assignedSurveyModels: Flow<List<SubmissionUiModel>> = combine(
        assignedSurveysFlow, allSubmissionsFlow, userIdFlow, _query
    ) { assigned, subs, uid, query ->
        val mySurveySubs = subs.filter { it.userId == uid && it.type == "survey" }
        val countByExam = mySurveySubs.groupingBy { it.parentId }.eachCount()
        val latestByExam = mySurveySubs.groupBy { it.parentId }
            .mapValues { entry -> entry.value.maxByOrNull { it.lastUpdateTime } }

        var list = assigned.map { assignedSurvey ->
            val exam = assignedSurvey.exam
            val latest = latestByExam[exam.id]
            SubmissionUiModel(
                id = exam.id,
                status = latest?.status,
                startTime = latest?.startTime ?: 0L,
                lastUpdateTime = latest?.lastUpdateTime ?: 0L,
                parentId = exam.id,
                userId = uid,
                submitterName = "",
                examTitle = exam.name ?: "",
                submissionCount = countByExam[exam.id] ?: 0,
                isTeam = assignedSurvey.isTeam,
                teamId = assignedSurvey.teamId
            )
        }

        if (query.isNotEmpty()) {
            list = list.filter { it.examTitle.contains(query, ignoreCase = true) }
        }

        list.sortedByDescending { it.lastUpdateTime }
    }.flowOn(dispatcherProvider.io)

    private val filteredProjections = combine(allSubmissionsFlow, _type, _query, exams, userIdFlow) { subs, type, query, examMap, uid ->
        submissionsRepository.getSubmissionProjections(subs, uid, type, query, examMap)
    }.flowOn(dispatcherProvider.io).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private val submissionBasedModels: Flow<List<SubmissionUiModel>> = combine(filteredProjections, exams) { projections, examsMap ->
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
    }

    val submissions: StateFlow<List<SubmissionUiModel>> = _type.flatMapLatest { type ->
        if (type == "survey") assignedSurveyModels else submissionBasedModels
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(type: String, query: String) {
        _type.value = type
        _query.value = query
    }
}
