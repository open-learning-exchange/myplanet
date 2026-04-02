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
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NetworkUtils.isNetworkConnectedFlow

@HiltViewModel
class BellDashboardViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val coursesRepository: CoursesRepository,
    private val teamsRepository: TeamsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Disconnected)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _completedCourses = MutableStateFlow<List<CourseCompletion>>(emptyList())
    val completedCourses: StateFlow<List<CourseCompletion>> = _completedCourses.asStateFlow()

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

sealed class NetworkStatus {
    object Disconnected : NetworkStatus()
    object Connecting : NetworkStatus()
    object Connected : NetworkStatus()
}
