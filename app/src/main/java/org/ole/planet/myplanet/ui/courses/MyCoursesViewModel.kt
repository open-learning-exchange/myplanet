package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler

@HiltViewModel
class MyCoursesViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val ratingRepository: RatingRepository,
    private val progressRepository: ProgressRepository,
    private val userProfileDbHandler: UserProfileDbHandler
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _searchTags = MutableStateFlow<List<String>>(emptyList())
    private val _gradeLevel = MutableStateFlow("")
    private val _subjectLevel = MutableStateFlow("")
    private val _uiState = MutableStateFlow(CoursesUiState())
    val uiState: StateFlow<CoursesUiState> = _uiState

    init {
        viewModelScope.launch {
            val coursesFlow = courseRepository.getMyCoursesFlow(userProfileDbHandler.userModel?.id ?: "")
            val ratingsFlow = ratingRepository.getRatingsFlow("course", userProfileDbHandler.userModel?.id ?: "")
            val progressFlow = progressRepository.getCourseProgress(userProfileDbHandler.userModel?.id ?: "")
            val filterFlow = combine(_searchQuery, _searchTags, _gradeLevel, _subjectLevel) { query, tags, grade, subject ->
                FilterParams(query, tags, grade, subject)
            }

            combine(coursesFlow, ratingsFlow, progressFlow, filterFlow) { courses, ratings, progress, filterParams ->
                val filteredCourses = filterCourses(courses, filterParams.query, filterParams.tags, filterParams.grade, filterParams.subject)
                CoursesUiState(
                    courses = filteredCourses.sortedWith(compareBy({ it?.isMyCourse }, { it?.courseTitle })),
                    ratings = ratings,
                    progress = progress
                )
            }.flatMapLatest { state ->
                flow { emit(state) }
            }.collect {
                _uiState.value = it
            }
        }
    }

    private data class FilterParams(val query: String, val tags: List<String>, val grade: String, val subject: String)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchTags(tags: List<String>) {
        _searchTags.value = tags
    }

    fun setGradeLevel(grade: String) {
        _gradeLevel.value = grade
    }

    fun setSubjectLevel(subject: String) {
        _subjectLevel.value = subject
    }

    private suspend fun filterCourses(
        courses: List<RealmMyCourse?>,
        query: String,
        tags: List<String>,
        grade: String,
        subject: String
    ): List<RealmMyCourse?> {
        return courses.filter { course ->
            val matchQuery = query.isEmpty() || course?.courseTitle?.contains(query, ignoreCase = true) == true
            val matchGrade = grade.isEmpty() || course?.gradeLevel?.contains(grade, ignoreCase = true) == true
            val matchSubject = subject.isEmpty() || course?.subjectLevel?.contains(subject, ignoreCase = true) == true
            val courseTags = courseRepository.getCourseTags(course?.courseId)
            val matchTags = tags.isEmpty() || courseTags.any { it in tags }
            matchQuery && matchGrade && matchSubject && matchTags
        }
    }
}
