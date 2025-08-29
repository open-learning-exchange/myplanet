package org.ole.planet.myplanet.ui.submission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.SubmissionRepository

@HiltViewModel
class MySubmissionViewModel @Inject constructor(
    private val submissionRepository: SubmissionRepository
) : ViewModel() {

    data class UiState(
        val submissions: List<RealmSubmission> = emptyList(),
        val exams: HashMap<String?, RealmStepExam> = hashMapOf()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadSubmissions(userId: String?, type: String?, search: String) {
        viewModelScope.launch {
            var subs = if (userId != null) {
                submissionRepository.getSubmissionsByUserId(userId)
            } else {
                emptyList()
            }

            subs = when (type) {
                "survey" -> subs.filter { it.type == "survey" }
                "survey_submission" -> subs.filter { it.type == "survey" && it.status != "pending" }
                else -> subs.filter { it.type != "survey" }
            }.sortedByDescending { it.lastUpdateTime }

            val examMap = submissionRepository.getExamMap(subs)

            if (search.isNotEmpty()) {
                subs = subs.filter { sub ->
                    examMap[sub.parentId]?.name?.contains(search, ignoreCase = true) == true
                }
            }

            val unique = subs
                .groupBy { it.parentId }
                .mapValues { entry -> entry.value.maxByOrNull { it.lastUpdateTime } }
                .values
                .filterNotNull()
                .toList()

            _uiState.value = UiState(unique, examMap)
        }
    }
}
