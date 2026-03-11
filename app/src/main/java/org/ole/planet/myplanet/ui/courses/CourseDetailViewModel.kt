package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.StepItem
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.UserSessionManager

sealed interface CourseDetailUiState {
    object Loading : CourseDetailUiState
    data class Success(
        val course: RealmMyCourse,
        val examCount: Int,
        val resources: List<RealmMyLibrary>,
        val downloadedResources: List<RealmMyLibrary>,
        val stepItems: List<StepItem>,
        val ratingSummary: JsonObject?,
        val user: RealmUser?
    ) : CourseDetailUiState
    data class Error(val message: String) : CourseDetailUiState
}

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val ratingsRepository: RatingsRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<CourseDetailUiState>(CourseDetailUiState.Loading)
    val uiState: StateFlow<CourseDetailUiState> = _uiState

    fun loadCourseDetail(courseId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = CourseDetailUiState.Loading
                val user = userSessionManager.getUserModel()
                val course = coursesRepository.getCourseByCourseId(courseId)
                if (course == null) {
                    _uiState.value = CourseDetailUiState.Error("Course not found")
                    return@launch
                }

                val examCount = coursesRepository.getCourseExamCount(courseId)
                val resources = coursesRepository.getCourseOnlineResources(courseId)
                val downloadedResources = coursesRepository.getCourseOfflineResources(courseId)
                val steps = coursesRepository.getCourseSteps(courseId)

                val stepItems = steps.map { step ->
                    val count = step.id?.let { submissionsRepository.getExamQuestionCount(it) } ?: 0
                    StepItem(
                        id = step.id,
                        stepTitle = step.stepTitle,
                        questionCount = count
                    )
                }

                var ratingSummaryObject: JsonObject? = null
                val userId = user?.id
                if (userId != null) {
                    val ratingSummary = ratingsRepository.getRatingSummary("course", courseId, userId)
                    ratingSummaryObject = JsonObject().apply {
                        addProperty("averageRating", ratingSummary.averageRating)
                        addProperty("total", ratingSummary.totalRatings)
                        ratingSummary.userRating?.let { addProperty("userRating", it) }
                    }
                }

                _uiState.value = CourseDetailUiState.Success(
                    course = course,
                    examCount = examCount,
                    resources = resources,
                    downloadedResources = downloadedResources,
                    stepItems = stepItems,
                    ratingSummary = ratingSummaryObject,
                    user = user
                )
            } catch (e: Exception) {
                _uiState.value = CourseDetailUiState.Error(e.message ?: "An error occurred")
            }
        }
    }

    fun refreshRatings(courseId: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is CourseDetailUiState.Success) {
                try {
                    val user = userSessionManager.getUserModel()
                    val userId = user?.id
                    if (userId != null) {
                        val ratingSummary = ratingsRepository.getRatingSummary("course", courseId, userId)
                        val ratingSummaryObject = JsonObject().apply {
                            addProperty("averageRating", ratingSummary.averageRating)
                            addProperty("total", ratingSummary.totalRatings)
                            ratingSummary.userRating?.let { addProperty("userRating", it) }
                        }
                        _uiState.value = currentState.copy(ratingSummary = ratingSummaryObject, user = user)
                    } else {
                        _uiState.value = currentState.copy(ratingSummary = null, user = user)
                    }
                } catch (e: Exception) {
                    // Optionally handle error
                }
            }
        }
    }
}
