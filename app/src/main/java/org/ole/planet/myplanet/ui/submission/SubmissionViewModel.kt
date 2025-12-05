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
import kotlinx.coroutines.flow.stateIn
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
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

    private val submissionsFlow = flow { emitAll(submissionRepository.getSubmissionsFlow(userId)) }

    val submissionItems: StateFlow<List<SubmissionItem>> =
        combine(
            _type,
            _query,
            submissionsFlow
        ) { type, query, submissions ->
            val filteredSubs = submissions.filter { sub ->
                when (type) {
                    "survey" -> sub.type == "survey"
                    "survey_submission" -> sub.type == "survey" && sub.status != "pending"
                    else -> sub.type != "survey"
                }
            }

            val examMap = submissionRepository.getExamMapForSubmissions(filteredSubs)
            val searchFilteredSubs = if (query.isNotEmpty()) {
                val examIds = examMap.filter { (_, exam) ->
                    exam.name?.contains(query, ignoreCase = true) == true
                }.keys
                filteredSubs.filter { examIds.contains(it.parentId) }
            } else {
                filteredSubs
            }

            val groupedSubmissions = searchFilteredSubs.groupBy { it.parentId }
            val latestSubmissions = groupedSubmissions
                .mapValues { entry -> entry.value.maxByOrNull { it.lastUpdateTime ?: 0 } }
                .values.filterNotNull()

            val submissionCountMap = groupedSubmissions.mapValues { it.value.size }
            val userIds = latestSubmissions.mapNotNull { it.userId }.toSet()
            val userNames = userIds.associateWith { id ->
                userRepository.getUserById(id)?.name
            }

            latestSubmissions.map { sub ->
                val count = submissionCountMap[sub.parentId] ?: 0
                SubmissionItem(
                    submission = sub,
                    examName = examMap[sub.parentId]?.name,
                    submissionCount = count,
                    userName = userNames[sub.userId]
                )
            }.sortedByDescending { it.submission.lastUpdateTime ?: 0 }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(type: String, query: String) {
        _type.value = type
        _query.value = query
    }
}
