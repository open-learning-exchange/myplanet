package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.UserRepository

sealed interface RatingPromptDecision {
    object Show : RatingPromptDecision
    object Skip : RatingPromptDecision
}

sealed interface TakeCourseUiState {
    object Loading : TakeCourseUiState
    object NotFound : TakeCourseUiState
    data class Success(
        val course: MyCourse,
        val steps: List<CourseStep>,
        val userModel: UserEntity?,
        val courseProgress: Int
    ) : TakeCourseUiState
}

@HiltViewModel
class TakeCourseViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val progressRepository: ProgressRepository,
    private val userRepository: UserRepository,
    private val ratingsRepository: RatingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TakeCourseUiState>(TakeCourseUiState.Loading)
    val uiState: StateFlow<TakeCourseUiState> = _uiState

    private var loadedCourseId: String? = null

    var hasOfferedJoinDialog = false
        private set

    fun markJoinDialogOffered() {
        hasOfferedJoinDialog = true
    }

    suspend fun logCourseVisit(courseId: String, courseTitle: String, userName: String) {
        coursesRepository.logCourseVisit(courseId, courseTitle, userName)
    }

    suspend fun getCurrentProgress(steps: List<CourseStep?>?, userId: String?, courseId: String?): Int {
        return coursesRepository.getCurrentProgress(steps, userId, courseId)
    }

    suspend fun getCourseStepData(stepId: String, userId: String?): CourseStepData {
        return coursesRepository.getCourseStepData(stepId, userId)
    }

    suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean {
        return coursesRepository.isStepCompleted(stepId, userId)
    }

    suspend fun getCourseById(courseId: String): MyCourse? {
        return coursesRepository.getCourseById(courseId)
    }

    suspend fun leaveCourse(courseId: String, userId: String): Result<Unit> {
        return coursesRepository.leaveCourse(courseId, userId)
    }

    suspend fun joinCourse(courseId: String, userId: String): Result<Unit> {
        return coursesRepository.joinCourse(courseId, userId)
    }

    suspend fun hasUnfinishedSurveys(courseId: String, userId: String?): Boolean {
        return coursesRepository.hasUnfinishedSurveys(courseId, userId)
    }

    suspend fun getRatingPromptDecision(courseId: String?, userId: String?): RatingPromptDecision {
        if (courseId.isNullOrEmpty() || userId.isNullOrEmpty()) {
            return RatingPromptDecision.Skip
        }
        val hasRated = try {
            val summary = ratingsRepository.getRatingSummary("course", courseId, userId)
            summary.userRating != null || summary.existingRating != null
        } catch (e: Exception) {
            false
        }
        return if (hasRated) RatingPromptDecision.Skip else RatingPromptDecision.Show
    }

    fun loadCourse(courseId: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && loadedCourseId == courseId && _uiState.value is TakeCourseUiState.Success) {
            return
        }
        loadedCourseId = courseId
        viewModelScope.launch {
            try {
                _uiState.value = TakeCourseUiState.Loading

                val userModel = userRepository.getUserModel()
                val course = coursesRepository.getCourseById(courseId)
                if (course == null) {
                    _uiState.value = TakeCourseUiState.NotFound
                    return@launch
                }

                val steps = coursesRepository.getCourseSteps(courseId)
                val progressMap = progressRepository.getCourseProgress(listOf(courseId), userModel?.id)
                val progress = progressMap[courseId]?.current ?: 0

                _uiState.value = TakeCourseUiState.Success(course, steps, userModel, progress)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
