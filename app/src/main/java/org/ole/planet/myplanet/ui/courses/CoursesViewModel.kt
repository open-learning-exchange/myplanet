package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.HashMap
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

data class CoursesUiState(
    val courses: List<Course> = emptyList(),
    val map: HashMap<String?, JsonObject> = HashMap(),
    val progressMap: HashMap<String?, JsonObject>? = null
)

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _coursesState = MutableStateFlow(CoursesUiState())
    val coursesState: StateFlow<CoursesUiState> = _coursesState

    fun loadCourses(isMyCourseLib: Boolean, userId: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            coroutineScope {
                val ratingsDeferred = async { coursesRepository.getCourseRatings(userId) }
                val progressDeferred = async { coursesRepository.getCourseProgress(userId) }

                val allCourses = coursesRepository.getAllCourses()
                val validCourses = allCourses.filter { !it.courseTitle.isNullOrBlank() }

                val myCourses = if (isMyCourseLib) {
                    coursesRepository.getMyCourses(userId, validCourses)
                } else {
                    emptyList()
                }

                val map = ratingsDeferred.await()
                val progressMap = progressDeferred.await()

                withContext(dispatcherProvider.main) {
                    processCourses(isMyCourseLib, userId, validCourses, myCourses, map, progressMap)
                }
            }
        }
    }

    fun filterCourses(
        searchText: String,
        gradeLevel: String,
        subjectLevel: String,
        tagNames: List<String>,
        userId: String?,
        isMyCourseLib: Boolean
    ) {
        viewModelScope.launch(dispatcherProvider.io) {
            coroutineScope {
                val coursesDeferred = async {
                    coursesRepository.filterCourses(searchText, gradeLevel, subjectLevel, tagNames)
                }
                val ratingsDeferred = async { coursesRepository.getCourseRatings(userId) }
                val progressDeferred = async { coursesRepository.getCourseProgress(userId) }

                val filteredCourses = coursesDeferred.await()
                val map = ratingsDeferred.await()
                val progressMap = progressDeferred.await()

                val myCourses = if (isMyCourseLib) {
                    coursesRepository.getMyCourses(userId, filteredCourses)
                } else {
                    emptyList()
                }

                withContext(dispatcherProvider.main) {
                    processCourses(isMyCourseLib, userId, filteredCourses, myCourses, map, progressMap)
                }
            }
        }
    }

    fun processCourses(
        isMyCourseLib: Boolean,
        userId: String?,
        validCourses: List<RealmMyCourse>,
        myCourses: List<RealmMyCourse>,
        map: HashMap<String?, JsonObject>,
        progressMap: HashMap<String?, JsonObject>?
    ) {
        val sortedCourseList = if (isMyCourseLib) {
            myCourses.forEach { it.isMyCourse = true }
            myCourses.sortedBy { it.courseTitle }
        } else {
            validCourses.forEach { it.isMyCourse = it.userId?.contains(userId) == true }
            validCourses.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
        }

        val mappedCourses = sortedCourseList.map { it.toCourse() }
        _coursesState.value = CoursesUiState(mappedCourses, map, progressMap)
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
