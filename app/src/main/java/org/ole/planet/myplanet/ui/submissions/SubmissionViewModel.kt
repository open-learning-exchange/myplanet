package org.ole.planet.myplanet.ui.submissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class SubmissionViewModel @Inject constructor(
    private val submissionRepository: SubmissionRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private data class SubmissionViewData(
        val submission: RealmSubmission,
        val submitterName: String,
    )

    private val _type = MutableStateFlow("")
    private val _query = MutableStateFlow("")

    private val userId by lazy { userRepository.getActiveUserId() }

    private val allSubmissionsFlow = flow {
        emitAll(submissionRepository.getSubmissionsFlow(userId))
    }.shareIn(viewModelScope, SharingStarted.Lazily, 1)

    val exams: StateFlow<HashMap<String?, RealmStepExam>> = allSubmissionsFlow.mapLatest { subs ->
        HashMap(submissionRepository.getExamMapForSubmissions(subs))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), hashMapOf())

    private val filteredSubmissionsRaw = combine(allSubmissionsFlow, _type, _query, exams) { subs, type, query, examMap ->
        var filtered = when (type) {
            "survey" -> subs.filter { it.userId == userId && it.type == "survey" }
            "survey_submission" -> subs.filter {
                it.userId == userId && it.type == "survey" && it.status != "pending"
            }
            else -> subs.filter { it.userId == userId && it.type != "survey" }
        }.sortedByDescending { it.lastUpdateTime ?: 0 }

        if (query.isNotEmpty()) {
            val examIds = examMap.filter { (_, exam) ->
                exam?.name?.contains(query, ignoreCase = true) == true
            }.keys
            filtered = filtered.filter { examIds.contains(it.parentId) }
        }

        val groupedSubmissions = filtered.groupBy { it.parentId }

        val uniqueSubmissions = groupedSubmissions
            .mapValues { entry -> entry.value.maxByOrNull { it.lastUpdateTime ?: 0 } }
            .values
            .filterNotNull()
            .map { sub ->
                val name = submissionRepository.getNormalizedSubmitterName(sub)
                val fallback = sub.userId?.let { userRepository.getUserById(it)?.name }
                SubmissionViewData(sub, name ?: fallback ?: "")
            }
            .sortedByDescending { it.submission.lastUpdateTime ?: 0 }

        val submissionCountMap = groupedSubmissions.mapValues { it.value.size }
            .mapKeys { entry ->
                groupedSubmissions[entry.key]?.maxByOrNull { it.lastUpdateTime ?: 0 }?.id
            }

        Triple(uniqueSubmissions, submissionCountMap, filtered)
    }.flowOn(Dispatchers.Default).shareIn(viewModelScope, SharingStarted.Lazily, 1)

    val submissions: StateFlow<List<RealmSubmission>> = filteredSubmissionsRaw.map { (uniqueSubmissions) ->
        uniqueSubmissions.map { viewData ->
            viewData.submission.apply {
                submitterName = viewData.submitterName
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val submissionCounts: StateFlow<Map<String?, Int>> = filteredSubmissionsRaw.map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setFilter(type: String, query: String) {
        _type.value = type
        _query.value = query
    }
}
