package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.UserRepository
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val databaseService: DatabaseService,
    private val courseRepository: CourseRepository,
    private val libraryRepository: LibraryRepository,
    private val userRepository: UserRepository
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
                
                val ratings = getRatings("course", userId)
                val progress = getCourseProgress(userId)
                
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
            databaseService.withRealmAsync { realm ->
                realm.where(RealmMyLibrary::class.java)
                    .`in`("courseId", courseIds.toTypedArray())
                    .equalTo("resourceOffline", false)
                    .isNotNull("resourceLocalAddress")
                    .findAll()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getRatings(type: String, userId: String?): Map<String, Int> {
        return try {
            databaseService.withRealmAsync { realm ->
                val ratings = realm.where(RealmRating::class.java)
                    .equalTo("type", type)
                    .equalTo("userId", userId)
                    .findAll()
                
                ratings.associate { (it.item ?: "") to (it.rate?.toInt() ?: 0) }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private suspend fun getCourseProgress(userId: String?): Map<String, RealmCourseProgress> {
        return try {
            databaseService.withRealmAsync { realm ->
                val progressList = realm.where(RealmCourseProgress::class.java)
                    .equalTo("userId", userId)
                    .findAll()
                
                progressList.associate { (it.courseId ?: "") to it }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

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
            databaseService.executeTransactionAsync { realm ->
                val activity = realm.createObject(RealmSearchActivity::class.java, UUID.randomUUID().toString())
                activity.user = userId ?: ""
                activity.time = Calendar.getInstance().timeInMillis
                activity.createdOn = userPlanetCode ?: ""
                activity.parentCode = userParentCode ?: ""
                activity.text = searchText
                activity.type = "courses"
                
                // Create filter object
                val filter = com.google.gson.JsonObject()
                val tagsArray = com.google.gson.JsonArray()
                tags.forEach { tag ->
                    tagsArray.add(tag.name)
                }
                filter.add("tags", tagsArray)
                filter.addProperty("doc.gradeLevel", gradeLevel)
                filter.addProperty("doc.subjectLevel", subjectLevel)
                
                activity.filter = com.google.gson.Gson().toJson(filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSyncState(state: SyncState) {
        _syncState.value = state
    }
}
