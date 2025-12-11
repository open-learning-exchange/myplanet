package org.ole.planet.myplanet.ui.submission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class SubmissionViewModel @Inject constructor(
    private val submissionRepository: SubmissionRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _type = MutableStateFlow("")
    private val _query = MutableStateFlow("")
    private val userId by lazy { userRepository.getActiveUserId() }
    private val allSubmissionsFlow = flow {
        emitAll(submissionRepository.getSubmissionsFlow(userId))
    }.shareIn(viewModelScope, SharingStarted.Lazily, 1)
    private val examsFlow = allSubmissionsFlow.mapLatest { subs ->
        HashMap(submissionRepository.getExamMapForSubmissions(subs))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), hashMapOf())
    val submissionItems: StateFlow<List<SubmissionItem>> = combine(allSubmissionsFlow, _type, _query, examsFlow) { subs, type, query, examMap ->
        val filtered = when (type) {
            "survey" -> subs.filter { it.userId == userId && it.type == "survey" }
            "survey_submission" -> subs.filter {
                it.userId == userId && it.type == "survey" && it.status != "pending"
            }
            else -> subs.filter { it.userId == userId && it.type != "survey" }
        }.sortedByDescending { it.lastUpdateTime ?: 0 }

        val queried = if (query.isNotEmpty()) {
            val examIds = examMap.filter { (_, exam) ->
                exam?.name?.contains(query, ignoreCase = true) == true
            }.keys
            filtered.filter { examIds.contains(it.parentId) }
        } else {
            filtered
        }

        val groupedSubmissions = queried.groupBy { it.parentId }

        val uniqueSubmissions = groupedSubmissions.mapValues { entry ->
            entry.value.maxByOrNull { it.lastUpdateTime ?: 0 }
        }.values.filterNotNull().onEach { sub ->
            val name = submissionRepository.getNormalizedSubmitterName(sub)
            val fallback = sub.userId?.let { userRepository.getUserById(it)?.name }
            sub.submitterName = name ?: fallback ?: ""
        }.sortedByDescending { it.lastUpdateTime ?: 0 }

        val submissionCountMap = groupedSubmissions.mapValues { it.value.size }

        uniqueSubmissions.map { sub ->
            SubmissionItem(
                submission = sub,
                exam = examMap[sub.parentId],
                count = submissionCountMap[sub.parentId] ?: 0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun setFilter(type: String, query: String) {
        _type.value = type
        _query.value = query
    }
}
