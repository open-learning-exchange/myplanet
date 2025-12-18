package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import javax.inject.Inject

data class CoursesUiState(
    val courses: List<RealmMyCourse> = emptyList(),
    val ratings: Map<String?, JsonObject> = emptyMap(),
    val progress: Map<String?, JsonObject> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isMyCourseLib: Boolean = false,
    val resources: List<RealmMyLibrary> = emptyList(),
)

@HiltViewModel
class CourseViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val userProfileDbHandler: UserProfileDbHandler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CoursesUiState())
    val uiState: StateFlow<CoursesUiState> = _uiState.asStateFlow()

    private val userId = userProfileDbHandler.userModel?.id
    private var courseJob: Job? = null

    init {
        loadCourses()
    }

    fun setMyCourseLib(isMyCourseLib: Boolean) {
        _uiState.update { it.copy(isMyCourseLib = isMyCourseLib) }
        loadCourses()
    }

    private fun loadCourses() {
        courseJob?.cancel()
        _uiState.update { it.copy(isLoading = true) }
        if (_uiState.value.isMyCourseLib) {
            courseJob = viewModelScope.launch {
                try {
                    val ratings = courseRepository.getRatings(userId)
                    val progress = courseRepository.getCourseProgress(userId)
                    courseRepository.getMyCoursesFlow(userId ?: "").collect { courseList ->
                        val courseIds = courseList.mapNotNull { it.id }
                        val resources = courseRepository.getCourseOfflineResources(courseIds)
                        _uiState.update {
                            it.copy(
                                courses = courseList,
                                ratings = ratings,
                                progress = progress,
                                isLoading = false,
                                resources = resources
                            )
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
            }
        } else {
            courseJob = viewModelScope.launch {
                try {
                    val courseList = courseRepository.filterCourses("", "", "", emptyList())
                    _uiState.update {
                        it.copy(
                            courses = courseList,
                            ratings = courseRepository.getRatings(userId),
                            progress = courseRepository.getCourseProgress(userId),
                            isLoading = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
            }
        }
    }

    fun filterCourses(
        searchText: String,
        gradeLevel: String,
        subjectLevel: String,
        tagNames: List<String>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val filteredCourses = courseRepository.filterCourses(
                    searchText,
                    gradeLevel,
                    subjectLevel,
                    tagNames
                )
                _uiState.update {
                    it.copy(
                        courses = filteredCourses,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
}
