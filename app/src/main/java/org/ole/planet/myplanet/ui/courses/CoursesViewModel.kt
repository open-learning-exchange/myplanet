package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.HashMap
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.RealmMyCourse

data class CoursesUiState(
    val courses: List<Course> = emptyList(),
    val map: HashMap<String?, JsonObject> = HashMap(),
    val progressMap: HashMap<String?, JsonObject>? = null
)

@HiltViewModel
class CoursesViewModel @Inject constructor() : ViewModel() {

    private val _coursesState = MutableStateFlow(CoursesUiState())
    val coursesState: StateFlow<CoursesUiState> = _coursesState

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
