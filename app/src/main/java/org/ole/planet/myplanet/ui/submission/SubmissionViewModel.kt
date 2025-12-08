package org.ole.planet.myplanet.ui.submission

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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler

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

    private val exams: StateFlow<HashMap<String?, RealmStepExam>> = allSubmissionsFlow.mapLatest { subs ->
        HashMap(submissionRepository.getExamMapForSubmissions(subs))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), hashMapOf())

    val submissionItems: StateFlow<List<SubmissionItem>> =
        combine(allSubmissionsFlow, _type, _query, exams) { subs, type, query, examMap ->
            val filtered = when (type) {
                "survey" -> subs.filter { it.userId == userId && it.type == "survey" }
                "survey_submission" -> subs.filter {
                    it.userId == userId && it.type == "survey" && it.status != "pending"
                }
                else -> subs.filter { it.userId == userId && it.type != "survey" }
            }.sortedByDescending { it.lastUpdateTime ?: 0 }

            val queryFiltered = if (query.isNotEmpty()) {
                val examIds = examMap.filter { (_, exam) ->
                    exam?.name?.contains(query, ignoreCase = true) == true
                }.keys
                filtered.filter { examIds.contains(it.parentId) }
            } else {
                filtered
            }

            val groupedSubmissions = queryFiltered.groupBy { it.parentId }

            val uniqueSubmissions = groupedSubmissions
                .mapValues { entry -> entry.value.maxByOrNull { it.lastUpdateTime ?: 0 } }
                .values
                .filterNotNull()
                .sortedByDescending { it.lastUpdateTime ?: 0 }

            val submissionCountMap = groupedSubmissions.mapValues { it.value.size }
                .mapKeys { entry ->
                    groupedSubmissions[entry.key]?.maxByOrNull { it.lastUpdateTime ?: 0 }?.id
                }

            val submitterIds = uniqueSubmissions.mapNotNull { it.userId }.toSet()
            val userNamesMap = submitterIds.mapNotNull { id ->
                userRepository.getUserById(id)?.let { user ->
                    user.name?.let { name -> id to name }
                }
            }.toMap()

            withContext(Dispatchers.Default) {
                uniqueSubmissions.map { sub ->
                    val resolvedName = runCatching {
                        sub.user?.takeIf { it.isNotBlank() }?.let { userJson ->
                            JSONObject(userJson).optString("name").takeIf { it.isNotBlank() }
                        }
                    }.getOrNull()

                    val finalName = resolvedName ?: userNamesMap[sub.userId]

                    SubmissionItem(
                        id = sub.id,
                        parentId = sub.parentId,
                        type = sub.type,
                        userId = sub.userId,
                        status = sub.status,
                        lastUpdateTime = sub.lastUpdateTime,
                        startTime = sub.startTime,
                        uploaded = sub.uploaded,
                        examName = examMap[sub.parentId]?.name,
                        submissionCount = submissionCountMap[sub.id] ?: 1,
                        userName = finalName
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    fun setFilter(type: String, query: String) {
        _type.value = type
        _query.value = query
    }
}
