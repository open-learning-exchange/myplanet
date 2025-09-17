package org.ole.planet.myplanet.ui.courses

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.CourseProgressRepository
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.repository.SearchRepository
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val libraryRepository: LibraryRepository,
    private val ratingRepository: RatingRepository,
    private val courseProgressRepository: CourseProgressRepository,
    private val searchRepository: SearchRepository,
    private val syncManager: SyncManager,
    private val sharedPrefManager: SharedPrefManager,
    @AppPreferences private val settings: SharedPreferences,
) : ViewModel() {

    private val serverUrlMapper = ServerUrlMapper()
    private var lastUserId: String? = null
    private var lastIsMyCourseLib: Boolean = false

    private val _coursesState = MutableStateFlow<CoursesUiState>(CoursesUiState.Loading)
    val coursesState: StateFlow<CoursesUiState> = _coursesState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    sealed class CoursesUiState {
        object Loading : CoursesUiState()
        data class Success(
            val courses: List<RealmMyCourse>,
            val ratings: Map<String?, JsonObject>,
            val progress: Map<String?, JsonObject>,
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
        lastUserId = userId
        lastIsMyCourseLib = isMyCourseLib
        viewModelScope.launch {
            fetchCourses(userId, isMyCourseLib)
        }
    }

    private suspend fun fetchCourses(userId: String?, isMyCourseLib: Boolean) {
        try {
            _coursesState.value = CoursesUiState.Loading

            val courses = courseRepository.getAllCourses().map { course ->
                course.isMyCourse = course.userId?.contains(userId) == true
                course
            }

            val filteredCourses = if (isMyCourseLib) {
                courses.filter { it.isMyCourse }
            } else {
                courses
            }

            val sortedCourses = filteredCourses.sortedWith(
                compareBy({ it.isMyCourse }, { it.courseTitle ?: "" })
            )

            val ratings = ratingRepository.getRatings("course", userId)
            val progress = courseProgressRepository.getCourseProgress(userId)

            _coursesState.value = CoursesUiState.Success(
                courses = sortedCourses,
                ratings = ratings,
                progress = progress,
            )
        } catch (e: Exception) {
            _coursesState.value = CoursesUiState.Error(e.message ?: "Unknown error")
        }
    }

    fun startCoursesSyncIfNeeded() {
        if (_syncState.value == SyncState.Syncing) return

        val isFastSync = settings.getBoolean("fastSync", false)
        if (!isFastSync || sharedPrefManager.isCoursesSynced()) {
            _syncState.value = SyncState.Idle
            return
        }

        val serverUrl = settings.getString("serverURL", "") ?: ""
        if (serverUrl.isBlank()) {
            _syncState.value = SyncState.Error("Server URL not configured")
            return
        }

        viewModelScope.launch {
            try {
                val mapping = serverUrlMapper.processUrl(serverUrl)
                withContext(Dispatchers.IO) {
                    serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
                        isServerReachable(url)
                    }
                }
                startSyncManager()
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() {
                _syncState.value = SyncState.Syncing
            }

            override fun onSyncComplete() {
                viewModelScope.launch {
                    sharedPrefManager.setCoursesSynced(true)
                    _syncState.value = SyncState.Success
                    lastUserId?.let { fetchCourses(it, lastIsMyCourseLib) }
                    _syncState.value = SyncState.Idle
                }
            }

            override fun onSyncFailed(msg: String?) {
                viewModelScope.launch {
                    _syncState.value = SyncState.Error(msg ?: "Unknown error")
                }
            }
        }, "full", listOf("courses"))
    }

    suspend fun addCoursesToMyList(courses: List<RealmMyCourse>) {
        try {
            val courseIds = courses.mapNotNull { it.courseId }
            courseRepository.updateMyCourseFlag(courseIds, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun removeCoursesFromMyList(courses: List<RealmMyCourse>) {
        try {
            val courseIds = courses.mapNotNull { it.courseId }
            courseRepository.updateMyCourseFlag(courseIds, false)
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

    suspend fun saveSearchActivity(
        userId: String?,
        userPlanetCode: String?,
        userParentCode: String?,
        searchText: String,
        tags: List<RealmTag>,
        gradeLevel: String,
        subjectLevel: String,
    ) {
        try {
            searchRepository.saveSearchActivity(
                userId,
                userPlanetCode,
                userParentCode,
                searchText,
                tags,
                gradeLevel,
                subjectLevel,
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
