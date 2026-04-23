package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.HashMap
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmTag
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
    val coursesState: StateFlow<CoursesUiState> = _coursesState

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

                    val (map, progressMap) = coroutineScope {
                        val ratingsDeferred = async { coursesRepository.getCourseRatings(userId) }
                        val progressDeferred = async { coursesRepository.getCourseProgress(userId) }
                        Pair(ratingsDeferred.await(), progressDeferred.await())
                    }

                    val allCourseIds = validCourses.mapNotNull { it.courseId }
                    val tagsMap = coursesRepository.getCourseTagsBulk(allCourseIds)
                        .mapValues { entry -> entry.value.map { it.toTag() } }

                    processCourses(isMyCourseLib, userId, validCourses, myCourses, map, progressMap, tagsMap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun filterCourses(isMyCourseLib: Boolean, userId: String?, searchText: String, selectedGrade: String, selectedSubject: String, tagNames: List<String>) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                val filteredCourses = coursesRepository.filterCourses(searchText, selectedGrade, selectedSubject, tagNames)
                val myCourses = filteredCourses.filter { it.userId?.contains(userId) == true }

                val (map, progressMap) = coroutineScope {
                    val ratingsDeferred = async { coursesRepository.getCourseRatings(userId) }
                    val progressDeferred = async { coursesRepository.getCourseProgress(userId) }
                    Pair(ratingsDeferred.await(), progressDeferred.await())
                }

                val tagsMap = _coursesState.value.tagsMap
                processCourses(isMyCourseLib, userId, filteredCourses, myCourses, map, progressMap, tagsMap)
            }
        }
    }

    private fun RealmMyCourse.toCourse(): Course {
        return Course(
            courseId = this.courseId ?: "",
            courseTitle = this.courseTitle ?: "",
            description = this.description ?: "",
            gradeLevel = this.gradeLevel ?: "",
            subjectLevel = this.subjectLevel ?: "",
            createdDate = this.createdDate,
            numberOfSteps = this.getNumberOfSteps(),
            isMyCourse = this.isMyCourse
        )
    }
}
