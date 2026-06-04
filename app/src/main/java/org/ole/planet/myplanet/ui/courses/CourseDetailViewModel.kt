package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.StepItem
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider

sealed interface CourseDetailUiState {
    object Loading : CourseDetailUiState
    data class Success(
        val course: RealmMyCourse,
        val examCount: Int,
        val resources: List<RealmMyLibrary>,
        val downloadedResources: List<RealmMyLibrary>,
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
    private val userSessionManager: UserSessionManager,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<CourseDetailUiState>(CourseDetailUiState.Loading)
    val uiState: StateFlow<CourseDetailUiState> = _uiState

    private val _stepItems = MutableStateFlow<List<StepItem>>(emptyList())
    val stepItems: StateFlow<List<StepItem>> = _stepItems

    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadCourseDetail(courseId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = CourseDetailUiState.Loading

            coursesRepository.getCourseByCourseIdFlow(courseId)
                .map { course ->
                    if (course == null) {
                        return@map CourseDetailUiState.Error("Course not found")
                    }

                    withContext(dispatcherProvider.io) {
                        val user = userSessionManager.getUserModel()
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
                        _stepItems.value = stepItems

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

                        CourseDetailUiState.Success(
                            course = course,
                            examCount = examCount,
                            resources = resources,
                            downloadedResources = downloadedResources,
                            ratingSummary = ratingSummaryObject,
                            user = user
                        )
                    }
                }
                .catch { e ->
                    emit(CourseDetailUiState.Error(e.message ?: "An error occurred"))
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun toggleStepDescription(stepId: String) {
        _stepItems.value = _stepItems.value.map { step ->
            if (step.id == stepId) {
                step.copy(isDescriptionVisible = !step.isDescriptionVisible)
            } else {
                if (step.isDescriptionVisible) step.copy(isDescriptionVisible = false) else step
            }
        }
    }

    fun refreshRatings(courseId: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is CourseDetailUiState.Success) {
                try {
                    val (ratingSummaryObject, user) = withContext(dispatcherProvider.io) {
                        val user = userSessionManager.getUserModel()
                        val userId = user?.id
                        if (userId != null) {
                            val ratingSummary = ratingsRepository.getRatingSummary("course", courseId, userId)
                            val ratingSummaryObject = JsonObject().apply {
                                addProperty("averageRating", ratingSummary.averageRating)
                                addProperty("total", ratingSummary.totalRatings)
                                ratingSummary.userRating?.let { addProperty("userRating", it) }
                            }
                            Pair(ratingSummaryObject, user)
                        } else {
                            Pair(null, user)
                        }
                    }
                    if (ratingSummaryObject != null) {
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
