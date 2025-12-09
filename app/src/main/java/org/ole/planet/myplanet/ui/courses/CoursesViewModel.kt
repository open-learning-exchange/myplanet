package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.repository.TagRepository
import javax.inject.Inject

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val ratingRepository: RatingRepository,
    private val progressRepository: ProgressRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _courses = MutableStateFlow<List<RealmMyCourse>>(emptyList())
    val courses: StateFlow<List<RealmMyCourse>> = _courses.asStateFlow()

    private val _ratings = MutableStateFlow<HashMap<String?, JsonObject>>(HashMap())
    val ratings: StateFlow<HashMap<String?, JsonObject>> = _ratings.asStateFlow()

    private val _progress = MutableStateFlow<HashMap<String?, JsonObject>>(HashMap())
    val progress: StateFlow<HashMap<String?, JsonObject>> = _progress.asStateFlow()

    private val _resources = MutableStateFlow<List<RealmMyLibrary>>(emptyList())
    val resources: StateFlow<List<RealmMyLibrary>> = _resources.asStateFlow()

    private val _tagsMap = MutableStateFlow<Map<String, List<RealmTag>>>(emptyMap())
    val tagsMap: StateFlow<Map<String, List<RealmTag>>> = _tagsMap.asStateFlow()

    fun fetchCourses(userId: String) {
        viewModelScope.launch {
            val courseList = courseRepository.getAllCourses(userId)
            val ratingsMap = ratingRepository.getAllRatings(userId, "course")
            val progressMap = progressRepository.getCourseProgress(userId)
            val tags = tagRepository.getAllCourseTags()

            _ratings.value = ratingsMap
            _progress.value = progressMap
            _tagsMap.value = tags
            _courses.value = courseList
        }
    }

    fun fetchResourcesForCourses(courseIds: List<String>) {
         viewModelScope.launch {
             _resources.value = courseRepository.getLibraryResourcesForCourses(courseIds)
         }
    }
}
