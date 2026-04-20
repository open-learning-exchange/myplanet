package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NetworkUtils.isNetworkConnectedFlow

@HiltViewModel
class BellDashboardViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val coursesRepository: CoursesRepository,
    private val teamsRepository: TeamsRepository,
    private val resourcesRepository: ResourcesRepository,
    private val lifeRepository: LifeRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Disconnected)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _completedCourses = MutableStateFlow<List<CourseCompletion>>(emptyList())
    val completedCourses: StateFlow<List<CourseCompletion>> = _completedCourses.asStateFlow()

    private val _coursePreviewItems = MutableStateFlow<List<CoursePreviewItem>>(emptyList())
    val coursePreviewItems: StateFlow<List<CoursePreviewItem>> = _coursePreviewItems.asStateFlow()

    private val _libraryPreviewItems = MutableStateFlow<List<LibraryPreviewItem>>(emptyList())
    val libraryPreviewItems: StateFlow<List<LibraryPreviewItem>> = _libraryPreviewItems.asStateFlow()

    private val _teamPreviewItems = MutableStateFlow<List<TeamPreviewItem>>(emptyList())
    val teamPreviewItems: StateFlow<List<TeamPreviewItem>> = _teamPreviewItems.asStateFlow()

    private val _lifePreviewItems = MutableStateFlow<List<LifePreviewItem>>(emptyList())
    val lifePreviewItems: StateFlow<List<LifePreviewItem>> = _lifePreviewItems.asStateFlow()

    private val _lastVisitedCourse = MutableStateFlow<LastVisitedCourseInfo?>(null)
    val lastVisitedCourse: StateFlow<LastVisitedCourseInfo?> = _lastVisitedCourse.asStateFlow()

    init {
        viewModelScope.launch {
            isNetworkConnectedFlow.collect { isConnected ->
                if (isConnected) {
                    updateNetworkStatus(NetworkStatus.Connecting)
                } else {
                    updateNetworkStatus(NetworkStatus.Disconnected)
                }
            }
        }
    }

    fun loadCoursePreviewItems(userId: String) {
        viewModelScope.launch {
            val items = withContext(dispatcherProvider.io) {
                val myCourses = coursesRepository.getMyCourses(userId)
                val allProgressRecords = progressRepository.getProgressRecords(userId)
                myCourses.filter { !it.courseTitle.isNullOrBlank() }.map { course ->
                    val passedSteps = allProgressRecords
                        .filter { it.courseId == course.courseId && it.passed }
                        .map { it.stepNum }
                        .toSet()
                        .size
                    val totalSteps = course.courseSteps?.size ?: 0
                    val percent = if (totalSteps > 0) (passedSteps * 100) / totalSteps else 0
                    CoursePreviewItem(
                        courseId = course.courseId ?: "",
                        courseTitle = course.courseTitle ?: "",
                        progressPercent = percent
                    )
                }
            }
            _coursePreviewItems.value = items
        }
    }

    fun loadLibraryPreviewItems(userId: String) {
        viewModelScope.launch {
            val items = withContext(dispatcherProvider.io) {
                resourcesRepository.getMyLibrary(userId)
                    .filter { !it.title.isNullOrBlank() }
                    .map { r ->
                        LibraryPreviewItem(
                            resourceId = r.id ?: "",
                            title = r.title ?: "",
                            subline = r.mediaType?.replaceFirstChar { it.uppercase() }
                                ?: r.resourceType?.replaceFirstChar { it.uppercase() }
                                ?: ""
                        )
                    }
            }
            _libraryPreviewItems.value = items
        }
    }

    fun loadTeamPreviewItems(userId: String) {
        viewModelScope.launch {
            val items = withContext(dispatcherProvider.io) {
                teamsRepository.getMyTeamsByUserId(userId)
                    .filter { !it.name.isNullOrBlank() }
                    .map { t ->
                        TeamPreviewItem(
                            teamId = t._id ?: "",
                            name = t.name ?: "",
                            teamType = t.teamType ?: t.type ?: ""
                        )
                    }
            }
            _teamPreviewItems.value = items
        }
    }

    fun loadLifePreviewItems(userId: String) {
        viewModelScope.launch {
            val items = withContext(dispatcherProvider.io) {
                lifeRepository.getMyLifeByUserId(userId)
                    .filter { it.isVisible && !it.title.isNullOrBlank() }
                    .map { l -> LifePreviewItem(imageId = l.imageId ?: "", title = l.title ?: "") }
            }
            _lifePreviewItems.value = items
        }
    }

    fun loadLastVisitedCourse(userId: String) {
        viewModelScope.launch {
            val info = withContext(dispatcherProvider.io) {
                val courseId = sharedPrefManager.getLastVisitedCourseId() ?: return@withContext null
                val course = coursesRepository.getCourseById(courseId) ?: return@withContext null
                val allProgress = progressRepository.getProgressRecords(userId)
                val passedSteps = allProgress
                    .filter { it.courseId == courseId && it.passed }
                    .map { it.stepNum }
                    .toSet()
                    .size
                val totalSteps = course.courseSteps?.size ?: 0
                val percent = if (totalSteps > 0) (passedSteps * 100) / totalSteps else 0
                LastVisitedCourseInfo(
                    courseId = courseId,
                    courseTitle = course.courseTitle ?: sharedPrefManager.getLastVisitedCourseTitle() ?: "",
                    progressPercent = percent,
                    totalSteps = totalSteps
                )
            }
            _lastVisitedCourse.value = info
        }
    }

    fun loadCompletedCourses(userId: String) {
        viewModelScope.launch {
            val completedCourses = withContext(dispatcherProvider.io) {
                val myCourses = coursesRepository.getMyCourses(userId)

                // Get all progress records for this user
                val allProgressRecords = progressRepository.getProgressRecords(userId)

                val completedCourses = mutableListOf<CourseCompletion>()
                myCourses.forEachIndexed { index, course ->
                    val hasValidId = !course.courseId.isNullOrBlank()
                    val hasValidTitle = !course.courseTitle.isNullOrBlank()

                    // Get progress records for this specific course
                    val courseProgressRecords = allProgressRecords.filter { it.courseId == course.courseId }

                    // Count UNIQUE steps that are passed (matches web: step.passed === true)
                    val passedStepNumbers = courseProgressRecords
                        .filter { it.passed }
                        .map { it.stepNum }
                        .toSet()
                    val passedSteps = passedStepNumbers.size
                    val totalSteps = course.courseSteps?.size ?: 0

                    // Web logic: ALL steps must be passed AND course must have at least one step
                    val allStepsPassed = passedSteps == totalSteps && totalSteps > 0

                    // Match web behavior: Show badge if ALL steps are passed AND course has steps
                    if (allStepsPassed && hasValidId && hasValidTitle) {
                        completedCourses.add(CourseCompletion(course.courseId, course.courseTitle))
                    }
                }
                completedCourses
            }

            _completedCourses.value = completedCourses
        }
    }

    private fun updateNetworkStatus(status: NetworkStatus) {
        _networkStatus.value = status
    }

    suspend fun checkServerConnection(serverUrl: String): Boolean {
        val reachable = isServerReachable(serverUrl)
        updateNetworkStatus(if (reachable) NetworkStatus.Connected else NetworkStatus.Disconnected)
        return reachable
    }

    suspend fun getTeamById(teamId: String) = teamsRepository.getTeamById(teamId)
}

data class CourseCompletion(val courseId: String?, val courseTitle: String?)

data class CoursePreviewItem(
    val courseId: String,
    val courseTitle: String,
    val progressPercent: Int
)

data class LibraryPreviewItem(val resourceId: String, val title: String, val subline: String)
data class TeamPreviewItem(val teamId: String, val name: String, val teamType: String)
data class LifePreviewItem(val imageId: String, val title: String)
data class LastVisitedCourseInfo(
    val courseId: String,
    val courseTitle: String,
    val progressPercent: Int,
    val totalSteps: Int
)

sealed class NetworkStatus {
    object Disconnected : NetworkStatus()
    object Connecting : NetworkStatus()
    object Connected : NetworkStatus()
}
