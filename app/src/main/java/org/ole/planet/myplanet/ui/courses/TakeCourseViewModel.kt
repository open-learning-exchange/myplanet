package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.datamanager.DatabaseService

@HiltViewModel
class TakeCourseViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val databaseService: DatabaseService,
    private val submissionRepository: SubmissionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<TakeCourseUiState>(TakeCourseUiState.Loading)
    val uiState: StateFlow<TakeCourseUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<TakeCourseUiEvent>()
    val uiEvent: SharedFlow<TakeCourseUiEvent> = _uiEvent.asSharedFlow()

    fun loadCourseData(courseId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val course = courseRepository.getCourseByCourseId(courseId)
                    val steps = courseRepository.getCourseSteps(courseId)
                    val user = userProfileDbHandler.userModel
                    databaseService.withRealm { realm ->
                        course?.let { RealmCourseActivity.createActivity(realm, user, it) }
                    }
                    val stepCompletionStatus = mutableMapOf<String, Boolean>()
                    steps.forEach { step ->
                        step.id?.let { stepId ->
                            stepCompletionStatus[stepId] = isStepCompleted(stepId, user?.id)
                        }
                    }
                    val unfinishedSurvey = hasUnfinishedSurvey(courseId, steps)
                    _uiState.value = TakeCourseUiState.Success(
                        course, steps, user, getCourseProgress(courseId), unfinishedSurvey, stepCompletionStatus
                    )
                }
            } catch (e: Exception) {
                _uiState.value = TakeCourseUiState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

    fun addRemoveCourse(courseId: String, userId: String?) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val course = courseRepository.getCourseByCourseId(courseId)
                    val isJoined = course?.userId?.contains(userId) == true
                    courseRepository.markCourseAdded(courseId, userId)
                    loadCourseData(courseId)
                    _uiEvent.emit(TakeCourseUiEvent.ShowToast(isJoined))
                }
            } catch (e: Exception) {
                _uiState.value = TakeCourseUiState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

    private fun getCourseProgress(courseId: String?): Int {
        return databaseService.withRealm { realm ->
            val user = userProfileDbHandler.userModel
            val courseProgressMap = RealmCourseProgress.getCourseProgress(realm, user?.id)
            courseProgressMap[courseId]?.asJsonObject?.get("current")?.asInt ?: 0
        }
    }

    private fun isStepCompleted(stepId: String?, userId: String?): Boolean {
        return databaseService.withRealm { realm ->
            RealmSubmission.isStepCompleted(realm, stepId, userId)
        }
    }

    private suspend fun hasUnfinishedSurvey(courseId: String?, steps: List<RealmCourseStep>): Boolean {
        val user = userProfileDbHandler.userModel
        val surveysByStep = databaseService.withRealm { realm ->
            steps.associate { step ->
                val surveys = realm.where(RealmStepExam::class.java)
                    .equalTo("stepId", step.id)
                    .equalTo("type", "surveys")
                    .findAll()
                step.id to realm.copyFromRealm(surveys)
            }
        }

        for ((_, surveys) in surveysByStep) {
            for (survey in surveys) {
                val exists = submissionRepository.existsSubmission(courseId, "survey", user?.id, survey.id)
                if (!exists) {
                    return true
                }
            }
        }
        return false
    }
}

sealed class TakeCourseUiState {
    data object Loading : TakeCourseUiState()
    data class Success(
        val course: RealmMyCourse?,
        val steps: List<RealmCourseStep>,
        val user: RealmUserModel?,
        val progress: Int,
        val hasUnfinishedSurvey: Boolean,
        val stepCompletionStatus: Map<String, Boolean>
    ) : TakeCourseUiState()
    data class Error(val message: String) : TakeCourseUiState()
}

sealed class TakeCourseUiEvent {
    data class ShowToast(val isJoined: Boolean) : TakeCourseUiEvent()
}
