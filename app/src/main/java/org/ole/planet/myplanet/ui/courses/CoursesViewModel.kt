package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.TagsRepository

data class CoursesUiState(
    val courses: List<Course> = emptyList(),
    val ratingsMap: HashMap<String?, JsonObject> = hashMapOf(),
    val progressMap: HashMap<String?, JsonObject> = hashMapOf(),
    val offlineResources: List<RealmMyLibrary> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val tagsRepository: TagsRepository,
    private val ratingsRepository: RatingsRepository,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoursesUiState())
    val uiState: StateFlow<CoursesUiState> = _uiState.asStateFlow()

    fun loadCourses(
        userId: String?,
        isMyCourseLib: Boolean,
        managedCourses: List<RealmMyCourse>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val courseList = managedCourses.map { course ->
                val copy = RealmMyCourse()
                copy.courseId = course.courseId
                copy.courseTitle = course.courseTitle
                copy.description = course.description
                copy.gradeLevel = course.gradeLevel
                copy.subjectLevel = course.subjectLevel
                copy.createdDate = course.createdDate
                course.userId?.forEach { id -> copy.setUserId(id) }
                copy.isMyCourse = if (isMyCourseLib) true else course.isMyCourse
                copy
            }

            val sortedCourseList = if (isMyCourseLib) {
                courseList.sortedBy { it.courseTitle }
            } else {
                courseList.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
            }

            val offlineResources = if (isMyCourseLib) {
                val courseIds = courseList.mapNotNull { it.id }
                coursesRepository.getCourseOfflineResources(courseIds)
            } else {
                emptyList()
            }

            val ratings = ratingsRepository.getCourseRatings(userId)
            val progress = progressRepository.getCourseProgress(userId)

            val courses = sortedCourseList.map { it.toCourse() }

            _uiState.value = _uiState.value.copy(
                courses = courses,
                ratingsMap = ratings,
                progressMap = progress,
                offlineResources = offlineResources,
                isLoading = false
            )
        }
    }

    fun filterCourses(
        userId: String?,
        isMyCourseLib: Boolean,
        searchText: String,
        gradeLevel: String,
        subjectLevel: String,
        tagNames: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val courses = coursesRepository.filterCourses(searchText, gradeLevel, subjectLevel, tagNames)
            val finalCourses = if (isMyCourseLib) {
                courses.filter { it.userId?.contains(userId ?: "") == true }
                    .onEach { it.isMyCourse = true }
                    .sortedBy { it.courseTitle }
            } else {
                courses.onEach { course ->
                    course.isMyCourse = course.userId?.contains(userId ?: "") == true
                }.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
            }

            val ratings = ratingsRepository.getCourseRatings(userId)
            val progress = progressRepository.getCourseProgress(userId)

            val mappedCourses = finalCourses.map { it.toCourse() }

            _uiState.value = _uiState.value.copy(
                courses = mappedCourses,
                ratingsMap = ratings,
                progressMap = progress,
                isLoading = false
            )
        }
    }

    suspend fun getCourseTags(courseId: String): List<RealmTag> {
        return tagsRepository.getTagsForCourse(courseId)
    }

    fun saveSearchActivity(
        searchText: String,
        userName: String,
        planetCode: String,
        parentCode: String,
        tags: List<RealmTag>,
        grade: String,
        subject: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            coursesRepository.saveSearchActivity(
                searchText, userName, planetCode, parentCode, tags, grade, subject
            )
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
