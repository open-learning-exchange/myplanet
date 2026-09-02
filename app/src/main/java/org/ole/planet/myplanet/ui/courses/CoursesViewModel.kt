package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.CourseProgressState
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.Tag
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

data class CoursesUiState(
    val courses: List<Course> = emptyList(),
    val progressMap: Map<String, CourseProgressState>? = null,
    val tagsMap: Map<String, List<Tag>> = emptyMap()
)

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val progressRepository: ProgressRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _coursesState = MutableStateFlow(CoursesUiState())
    val coursesState: StateFlow<CoursesUiState> = _coursesState.asStateFlow()

    private var isTitleAscending = false
    private var isDateAscending = true
    private var activeSort: SortType? = null
    private var sortJob: Job? = null

    var currentFilterState: FilterState = FilterState("", "", "", emptyList())
        private set

    enum class SortType { TITLE, DATE }

    fun toggleTitleSort() {
        isTitleAscending = !isTitleAscending
        activeSort = SortType.TITLE
        applySort()
    }

    fun toggleDateSort() {
        isDateAscending = !isDateAscending
        activeSort = SortType.DATE
        applySort()
    }

    private fun applySort() {
        sortJob?.cancel()
        sortJob = viewModelScope.launch {
            val currentCourses = _coursesState.value.courses
            val sortedCourses = withContext(dispatcherProvider.default) {
                sortCourses(currentCourses)
            }
            if (_coursesState.value.courses === currentCourses) {
                _coursesState.value = _coursesState.value.copy(courses = sortedCourses)
            }
        }
    }

    private fun sortCourses(courses: List<Course>): List<Course> {
        return when (activeSort) {
            SortType.TITLE -> if (isTitleAscending) {
                courses.sortedBy { it.courseTitle.lowercase() }
            } else {
                courses.sortedByDescending { it.courseTitle.lowercase() }
            }
            SortType.DATE -> if (isDateAscending) {
                courses.sortedBy { it.createdDate }
            } else {
                courses.sortedByDescending { it.createdDate }
            }
            null -> courses
        }
    }

    private fun processCourses(
        isMyCourseLib: Boolean,
        userId: String?,
        validCourses: List<MyCourse>,
        myCourses: List<MyCourse>,
        progressMap: Map<String, CourseProgressState>?,
        tagsMap: Map<String, List<Tag>>
    ): CoursesUiState {
        val sortedCourseList = if (isMyCourseLib) {
            myCourses.forEach { it.isMyCourse = true }
            myCourses.sortedBy { it.courseTitle }
        } else {
            validCourses.forEach { it.isMyCourse = it.userId?.contains(userId) == true }
            validCourses.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
        }

        val mappedCourses = sortCourses(sortedCourseList.map { it.toCourse() })
        return CoursesUiState(mappedCourses, progressMap, tagsMap)
    }

    fun loadCourses(isMyCourseLib: Boolean, userId: String?) {
        viewModelScope.launch {
            val newState = withContext(dispatcherProvider.io) {
                try {
                    val allCourses = coursesRepository.getAllCourses()
                    val validCourses = allCourses.filter { !it.courseTitle.isNullOrBlank() }

                    val myCourses = if (isMyCourseLib) {
                        coursesRepository.getMyCourses(userId, validCourses)
                    } else {
                        emptyList()
                    }

                    val allCourseIds = validCourses.mapNotNull { it.courseId }

                    val progressMap = coroutineScope {
                        val progressDeferred = async {
                            progressRepository.getCourseProgress(allCourseIds, userId)
                        }
                        progressDeferred.await()
                    }

                    val tagsMap = coursesRepository.getCourseTagsBulk(allCourseIds)
                        .mapValues { entry -> entry.value.map { it.toTag() } }

                    if (currentFilterState.isActive) {
                        filterCoursesInternal(
                            isMyCourseLib = isMyCourseLib,
                            userId = userId,
                            filterState = currentFilterState,
                            progressMap = progressMap,
                            tagsMap = tagsMap
                        )
                    } else {
                        processCourses(isMyCourseLib, userId, validCourses, myCourses, progressMap, tagsMap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            if (newState != null) {
                _coursesState.value = newState
            }
        }
    }

    fun filterCourses(
        isMyCourseLib: Boolean,
        userId: String?,
        searchText: String,
        selectedGrade: String,
        selectedSubject: String,
        tagNames: List<String>,
        progressFilter: String = "",
        tags: List<TagEntity> = emptyList()
    ) {
        val filterState = FilterState(searchText, selectedGrade, selectedSubject, tagNames, progressFilter, tags)
        currentFilterState = filterState
        viewModelScope.launch {
            val newState = withContext(dispatcherProvider.io) {
                val progressMap = _coursesState.value.progressMap
                val tagsMap = _coursesState.value.tagsMap
                filterCoursesInternal(
                    isMyCourseLib = isMyCourseLib,
                    userId = userId,
                    filterState = filterState,
                    progressMap = progressMap,
                    tagsMap = tagsMap
                )
            }
            _coursesState.value = newState
        }
    }

    private suspend fun filterCoursesInternal(
        isMyCourseLib: Boolean,
        userId: String?,
        filterState: FilterState,
        progressMap: Map<String, CourseProgressState>?,
        tagsMap: Map<String, List<Tag>>
    ): CoursesUiState {
        val filteredCourses = coursesRepository.filterCourses(
            filterState.searchText, filterState.grade, filterState.subject, filterState.tagNames
        )
        val myCourses = coursesRepository.getMyCourses(userId, filteredCourses)
        val baseCourses = if (isMyCourseLib) myCourses else filteredCourses

        val progressFilter = filterState.progressFilter
        val progressFilteredCourses = if (progressFilter.isEmpty() || progressMap == null) {
            baseCourses
        } else {
            baseCourses.filter { course ->
                val courseKey = course.courseId.takeIf { !it.isNullOrBlank() }
                    ?: course.id.takeIf { !it.isNullOrBlank() }
                    ?: course._id
                val p = progressMap[courseKey] ?: progressMap[course.courseId] ?: progressMap[course.id]
                val current = p?.current ?: 0
                val max = p?.max?.takeIf { it > 0 } ?: course.getNumberOfSteps()
                when (progressFilter) {
                    "Not Started" -> current == 0
                    "In Progress" -> current > 0 && (max == 0 || current < max)
                    "Completed"   -> max > 0 && current >= max
                    else -> true
                }
            }
        }

        return if (isMyCourseLib) {
            processCourses(isMyCourseLib, userId, filteredCourses, progressFilteredCourses, progressMap, tagsMap)
        } else {
            processCourses(isMyCourseLib, userId, progressFilteredCourses, myCourses, progressMap, tagsMap)
        }
    }

    fun removeCourses(courseIds: List<String>, userId: String, deleteProgress: Boolean, onComplete: () -> Unit) {
        if (courseIds.isEmpty()) return
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                coursesRepository.removeCoursesFromShelf(courseIds, userId)
                if (deleteProgress) {
                    courseIds.forEach { courseId ->
                        coursesRepository.deleteCourseProgress(courseId)
                    }
                }
            }
            onComplete()
        }
    }
}
