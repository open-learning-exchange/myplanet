package org.ole.planet.myplanet.ui.submission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import javax.inject.Inject

@HiltViewModel
class MySubmissionViewModel @Inject constructor(
    private val submissionRepository: SubmissionRepository,
    private val userRepository: UserRepository,
    private val userProfileDbHandler: UserProfileDbHandler
) : ViewModel() {

    private val _submissions = MutableStateFlow<List<RealmSubmission>>(emptyList())
    val submissions: StateFlow<List<RealmSubmission>> = _submissions

    private val _exams = MutableStateFlow<HashMap<String?, RealmStepExam>>(hashMapOf())
    val exams: StateFlow<HashMap<String?, RealmStepExam>> = _exams

    private val _userNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val userNames: StateFlow<Map<String, String>> = _userNames

    private var allSubmissions: List<RealmSubmission> = emptyList()

    fun loadSubmissions(type: String, query: String) {
        viewModelScope.launch {
            if (allSubmissions.isEmpty()) {
                val user = userProfileDbHandler.userModel
                allSubmissions = submissionRepository.getSubmissionsByUserId(user?.id ?: "")
                _exams.value = HashMap(submissionRepository.getExamMapForSubmissions(allSubmissions))
            }
            filterSubmissions(type, query)
        }
    }

    private suspend fun filterSubmissions(type: String, query: String) {
        val user = userProfileDbHandler.userModel
        var filtered = allSubmissions

        filtered = when (type) {
            "survey" -> filtered.filter { it.userId == user?.id && it.type == "survey" }
            "survey_submission" -> filtered.filter {
                it.userId == user?.id && it.type == "survey" && it.status != "pending"
            }
            else -> filtered.filter { it.userId == user?.id && it.type != "survey" }
        }.sortedByDescending { it.lastUpdateTime ?: 0 }

        if (query.isNotEmpty()) {
            val examIds = _exams.value.filter { (_, exam) ->
                exam?.name?.contains(query, ignoreCase = true) == true
            }.keys
            filtered = filtered.filter { examIds.contains(it.parentId) }
        }

        val uniqueSubmissions = filtered
            .groupBy { it.parentId }
            .mapValues { entry -> entry.value.maxByOrNull { it.lastUpdateTime ?: 0 } }
            .values
            .filterNotNull()
            .toList()

        _submissions.value = uniqueSubmissions

        val submitterIds = uniqueSubmissions.mapNotNull { it.userId }.toSet()
        val userNameMap = submitterIds.mapNotNull { id ->
            val userModel = userRepository.getUserById(id)
            val displayName = userModel?.name
            if (displayName.isNullOrBlank()) {
                null
            } else {
                id to displayName
            }
        }.toMap()
        _userNames.value = userNameMap
    }
}
