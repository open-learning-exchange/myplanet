package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.StepItem
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.utils.MarkdownUtils

sealed interface CourseDetailUiState {
    object Loading : CourseDetailUiState
    data class Success(
        val course: RealmMyCourse,
        val markdownDescription: String,
        val examCount: Int,
        val resources: List<RealmMyLibrary>,
        val downloadedResources: List<RealmMyLibrary>,
        val ratingSummary: RatingSummary?,
        val user: RealmUser?
    ) : CourseDetailUiState
    data class Error(val message: String) : CourseDetailUiState
}

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    private val courseDetailProvider: CourseDetailProvider,
    private val ratingSummaryProvider: RatingSummaryProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<CourseDetailUiState>(CourseDetailUiState.Loading)
    val uiState: StateFlow<CourseDetailUiState> = _uiState

    private val _stepItems = MutableStateFlow<List<StepItem>>(emptyList())
    val stepItems: StateFlow<List<StepItem>> = _stepItems

    private var loadJob: Job? = null

    fun loadCourseDetail(courseId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = CourseDetailUiState.Loading

            courseDetailProvider(courseId)
                .catch { e ->
                    _uiState.value = CourseDetailUiState.Error(e.message ?: "An error occurred")
                }
                .collect { courseDetail ->
                    if (courseDetail == null) {
                        _uiState.value = CourseDetailUiState.Error("Course not found")
                        return@collect
                    }

                    _stepItems.value = courseDetail.steps

                    val markdownDescription = MarkdownUtils.prependBaseUrlToImages(
                        courseDetail.course.description,
                        "file://${MainApplication.context.getExternalFilesDir(null)}/ole/",
                        600, 350
                    )

                    _uiState.value = CourseDetailUiState.Success(
                        course = courseDetail.course,
                        markdownDescription = markdownDescription,
                        examCount = courseDetail.examCount,
                        resources = courseDetail.resources,
                        downloadedResources = courseDetail.downloadedResources,
                        ratingSummary = courseDetail.ratingSummary,
                        user = courseDetail.user
                    )
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
                    val summaryModel = ratingSummaryProvider(courseId)
                    _uiState.value = currentState.copy(
                        ratingSummary = summaryModel.ratingSummary,
                        user = summaryModel.user
                    )
                } catch (e: Exception) {
                    // Optionally handle error
                }
            }
        }
    }
}
