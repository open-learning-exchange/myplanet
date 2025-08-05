package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.CourseProgressRepository
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.repository.SearchRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val libraryRepository: LibraryRepository,
    private val userRepository: UserRepository,
    private val ratingRepository: RatingRepository,
    private val courseProgressRepository: CourseProgressRepository,
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _coursesState = MutableStateFlow<CoursesUiState>(CoursesUiState.Loading)
    val coursesState: StateFlow<CoursesUiState> = _coursesState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    sealed class CoursesUiState {
        object Loading : CoursesUiState()
        data class Success(
            val courses: List<RealmMyCourse>,
            val ratings: Map<String, Int>,
            val progress: Map<String, RealmCourseProgress>
        ) : CoursesUiState()
        data class Error(val message: String) : CoursesUiState()
    }

    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        object Success : SyncState()
        data class Error(val message: String) : SyncState()
    }

    fun loadCourses(userId: String?, isMyCourseLib: Boolean = false) {
        viewModelScope.launch {
            try {
                _coursesState.value = CoursesUiState.Loading
                
                val courses = courseRepository.getAllCourses()
                val filteredCourses = if (isMyCourseLib) {
                    courses.filter { it.isMyCourse }
                } else {
                    courses
                }
                
                val ratings = ratingRepository.getRatings("course", userId)
                val progress = courseProgressRepository.getCourseProgress(userId)
                
                _coursesState.value = CoursesUiState.Success(
                    courses = filteredCourses,
                    ratings = ratings,
                    progress = progress
                )
            } catch (e: Exception) {
                _coursesState.value = CoursesUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun addCoursesToMyList(courses: List<RealmMyCourse>) {
        try {
            courses.forEach { course ->
                val updatedCourse = course.apply {
                    isMyCourse = true
                }
                courseRepository.saveCourse(updatedCourse)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun removeCoursesFromMyList(courses: List<RealmMyCourse>) {
        try {
            courses.forEach { course ->
                val updatedCourse = course.apply {
                    isMyCourse = false
                }
                courseRepository.saveCourse(updatedCourse)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getCourseLibraryItems(courseIds: List<String>): List<RealmMyLibrary> {
        return try {
            libraryRepository.getCourseLibraryItems(courseIds)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Ratings and course progress retrieval are handled by repositories

    suspend fun saveSearchActivity(
        userId: String?,
        userPlanetCode: String?,
        userParentCode: String?,
        searchText: String,
        tags: List<RealmTag>,
        gradeLevel: String,
        subjectLevel: String
    ) {
        try {
            searchRepository.saveSearchActivity(
                userId,
                userPlanetCode,
                userParentCode,
                searchText,
                tags,
                gradeLevel,
                subjectLevel
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSyncState(state: SyncState) {
        _syncState.value = state
    }
}
