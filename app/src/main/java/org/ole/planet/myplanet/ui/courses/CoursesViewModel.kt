package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.HashMap
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.Tag
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

data class CoursesUiState(
    val courses: List<Course> = emptyList(),
    val map: HashMap<String?, JsonObject> = HashMap(),
    val progressMap: HashMap<String?, JsonObject>? = null,
    val tagsMap: Map<String, List<Tag>> = emptyMap()
)

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _coursesState = MutableStateFlow(CoursesUiState())
    val coursesState: StateFlow<CoursesUiState> = _coursesState.asStateFlow()

    private fun processCourses(
        isMyCourseLib: Boolean,
        userId: String?,
        validCourses: List<RealmMyCourse>,
        myCourses: List<RealmMyCourse>,
        map: HashMap<String?, JsonObject>,
        progressMap: HashMap<String?, JsonObject>?,
        tagsMap: Map<String, List<Tag>>
    ) {
        val sortedCourseList = if (isMyCourseLib) {
            myCourses.forEach { it.isMyCourse = true }
            myCourses.sortedBy { it.courseTitle }
        } else {
            validCourses.forEach { it.isMyCourse = it.userId?.contains(userId) == true }
            validCourses.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
        }

        val mappedCourses = sortedCourseList.map { it.toCourse() }
        _coursesState.value = CoursesUiState(mappedCourses, map, progressMap, tagsMap)
    }

    fun loadCourses(isMyCourseLib: Boolean, userId: String?) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                try {
                    val allCourses = coursesRepository.getAllCourses()
                    val validCourses = allCourses.filter { !it.courseTitle.isNullOrBlank() }

                    val myCourses = if (isMyCourseLib) {
                        coursesRepository.getMyCourses(userId, validCourses)
                    } else {
                        emptyList()
                    }

                    val allCourseIds = validCourses.mapNotNull { it.courseId }

                    val (map, progressMap) = coroutineScope {
                        val ratingsDeferred = async { coursesRepository.getCourseRatings(userId) }
                        val progressDeferred = async {
                            if (isMyCourseLib) {
                                coursesRepository.getCourseProgress(userId, allCourseIds)
                            } else {
                                null
                            }
                        }
                        Pair(ratingsDeferred.await(), progressDeferred.await())
                    }

                    val tagsMap = coursesRepository.getCourseTagsBulk(allCourseIds)
                        .mapValues { entry -> entry.value.map { it.toTag() } }

                    processCourses(isMyCourseLib, userId, validCourses, myCourses, map, progressMap, tagsMap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun refreshCourseRatings(userId: String?) {
        withContext(dispatcherProvider.io) {
            try {
                val map = coursesRepository.getCourseRatings(userId)
                _coursesState.value = _coursesState.value.copy(map = map)
            } catch (e: Exception) {
                e.printStackTrace()
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
        progressFilter: String = ""
    ) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                val filteredCourses = coursesRepository.filterCourses(searchText, selectedGrade, selectedSubject, tagNames)
                val myCourses = filteredCourses.filter { it.userId?.contains(userId) == true }
                val map = _coursesState.value.map
                val progressMap = _coursesState.value.progressMap
                val tagsMap = _coursesState.value.tagsMap

                val progressFilteredCourses = if (progressFilter.isEmpty() || progressMap == null) {
                    myCourses
                } else {
                    myCourses.filter { course ->
                        val p = progressMap[course.courseId]
                        val current = p?.get("current")?.asInt ?: 0
                        val max = p?.get("max")?.asInt ?: 0
                        when (progressFilter) {
                            "Not Started" -> current == 0
                            "In Progress" -> current > 0 && current < max
                            "Completed"   -> max > 0 && current >= max
                            else -> true
                        }
                    }
                }

                processCourses(isMyCourseLib, userId, filteredCourses, progressFilteredCourses, map, progressMap, tagsMap)
            }
        }
    }

    fun removeCourses(courseIds: List<String>, userId: String, deleteProgress: Boolean, onComplete: () -> Unit) {
        if (courseIds.isEmpty()) return
        viewModelScope.launch(dispatcherProvider.io) {
            courseIds.forEach { courseId ->
                coursesRepository.removeCourseFromShelf(courseId, userId)
                if (deleteProgress) {
                    coursesRepository.deleteCourseProgress(courseId)
                }
            }
            withContext(dispatcherProvider.main) {
                onComplete()
            }
        }
    }
}
