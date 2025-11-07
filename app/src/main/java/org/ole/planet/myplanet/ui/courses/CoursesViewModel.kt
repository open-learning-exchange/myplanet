package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.CourseRepository
import javax.inject.Inject

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val courseRepository: CourseRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<CoursesUiState>()
    val uiState: LiveData<CoursesUiState> = _uiState

    private var _allCourses = listOf<RealmMyCourse?>()

    private var searchQuery = ""
    private var subjectLevel = ""
    private var gradeLevel = ""
    private var searchTags = listOf<RealmTag>()

    fun loadCourses(userId: String?) {
        viewModelScope.launch {
            _allCourses = courseRepository.getAllCourses()
            val ratings = courseRepository.getRatings(userId)
            val progress = courseRepository.getCourseProgress(userId)
            _uiState.postValue(CoursesUiState(courses = _allCourses, ratings = ratings, progress = progress))
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        filterCourses()
    }

    fun setGradeLevel(gradeLevel: String) {
        this.gradeLevel = gradeLevel
        filterCourses()
    }

    fun setSubjectLevel(subjectLevel: String) {
        this.subjectLevel = subjectLevel
        filterCourses()
    }

    fun setSearchTags(tags: List<RealmTag>) {
        searchTags = tags
        filterCourses()
    }

    private fun filterCourses() {
        var filteredList = _allCourses
        if (searchQuery.isNotEmpty()) {
            filteredList = filteredList.filter {
                it?.courseTitle?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        if (gradeLevel.isNotEmpty()) {
            filteredList = filteredList.filter {
                it?.gradeLevel?.contains(gradeLevel, ignoreCase = true) == true
            }
        }
        if (subjectLevel.isNotEmpty()) {
            filteredList = filteredList.filter {
                it?.subjectLevel?.contains(subjectLevel, ignoreCase = true) == true
            }
        }
        if (searchTags.isNotEmpty()) {
            filteredList = filteredList.filter { course ->
                course?.tags?.any { tag -> searchTags.any { it.id == tag.id } } == true
            }
        }
        _uiState.postValue(_uiState.value?.copy(courses = filteredList))
    }
}
