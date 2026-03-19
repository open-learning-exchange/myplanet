package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyCourse
import javax.inject.Inject

import java.util.HashMap

data class CoursesUiState(
    val courses: List<RealmMyCourse> = emptyList(),
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
        allCourses: List<RealmMyCourse>,
        myCourses: List<RealmMyCourse>,
        map: HashMap<String?, JsonObject>,
        progressMap: HashMap<String?, JsonObject>?
    ) {
        viewModelScope.launch {
            val validCourses = allCourses.filter { !it.courseTitle.isNullOrBlank() }
            val validMyCourses = myCourses.filter { !it.courseTitle.isNullOrBlank() }
            val sortedCourseList = if (isMyCourseLib) {
                validMyCourses.forEach { it.isMyCourse = true }
                validMyCourses.sortedBy { it.courseTitle }
            } else {
                validCourses.forEach { it.isMyCourse = it.userId?.contains(userId) == true }
                validCourses.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
            }
            _coursesState.value = CoursesUiState(sortedCourseList, map, progressMap)
        }
    }
}
