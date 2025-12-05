package org.ole.planet.myplanet.ui.submission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.submission.SubmissionItem
import javax.inject.Inject

@HiltViewModel
class SubmissionViewModel @Inject constructor(
    private val submissionRepository: SubmissionRepository,
    private val userRepository: UserRepository,
    private val userProfileDbHandler: UserProfileDbHandler
) : ViewModel() {

    private val _type = MutableStateFlow("")
    private val _query = MutableStateFlow("")

    private val userId by lazy { userProfileDbHandler.userModel?.id ?: "" }

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
            .sortedByDescending { it.lastUpdateTime ?: 0 }

        val submissionCountMap = groupedSubmissions.mapValues { it.value.size }
            .mapKeys { entry ->
                groupedSubmissions[entry.key]?.maxByOrNull { it.lastUpdateTime ?: 0 }?.id
            }

        Triple(uniqueSubmissions, submissionCountMap, filtered)
    }.shareIn(viewModelScope, SharingStarted.Lazily, 1)

    val submissionItems: StateFlow<List<SubmissionItem>> =
        combine(
            filteredSubmissionsRaw,
            exams
        ) { (uniqueSubmissions, submissionCountMap), examMap ->
            val submitterIds = uniqueSubmissions.mapNotNull { it.userId }.toSet()
            val userNames = submitterIds.associateWith { id ->
                userRepository.getUserById(id)?.name?.takeIf { it.isNotBlank() }
            }

            uniqueSubmissions.map { sub ->
                SubmissionItem(
                    id = sub.id,
                    parentId = sub.parentId,
                    type = sub.type,
                    userId = sub.userId,
                    status = sub.status,
                    startTime = sub.startTime,
                    lastUpdateTime = sub.lastUpdateTime,
                    examName = examMap[sub.parentId]?.name,
                    userName = userNames[sub.userId],
                    count = submissionCountMap[sub.id] ?: 1
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(type: String, query: String) {
        _type.value = type
        _query.value = query
    }
}
