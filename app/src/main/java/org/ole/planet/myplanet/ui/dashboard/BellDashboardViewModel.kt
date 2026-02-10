package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.NetworkUtils.isNetworkConnectedFlow

@HiltViewModel
class BellDashboardViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val coursesRepository: CoursesRepository,
    private val teamsRepository: TeamsRepository,
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
            android.util.Log.d("BadgeConditions", "========== LOADING BADGES (WEB MATCHING MODE) ==========")
            android.util.Log.d("BadgeConditions", "Starting badge load for userId: $userId")

            val myCourses = coursesRepository.getMyCourses(userId)
            android.util.Log.d("BadgeConditions", "Total user courses found: ${myCourses.size}")

            // Get all progress records for this user
            val allProgressRecords = progressRepository.getProgressRecords(userId)
            android.util.Log.d("BadgeConditions", "Total progress records found: ${allProgressRecords.size}")

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

                android.util.Log.d("BadgeConditions", "Course #${index + 1}: ${course.courseTitle}")
                android.util.Log.d("BadgeConditions", "  - Course ID: ${course.courseId}")
                android.util.Log.d("BadgeConditions", "  - Total steps: $totalSteps")
                android.util.Log.d("BadgeConditions", "  - Passed steps: $passedSteps")
                android.util.Log.d("BadgeConditions", "  - All steps passed: $allStepsPassed")
                android.util.Log.d("BadgeConditions", "  - Has Valid ID: $hasValidId")
                android.util.Log.d("BadgeConditions", "  - Has Valid Title: $hasValidTitle")

                // Match web behavior: Show badge if ALL steps are passed AND course has steps
                if (allStepsPassed && hasValidId && hasValidTitle) {
                    completedCourses.add(CourseCompletion(course.courseId, course.courseTitle))
                    android.util.Log.d("BadgeConditions", "  ✓ ADDED TO BADGE LIST (all steps passed)")
                } else {
                    when {
                        totalSteps == 0 -> android.util.Log.d("BadgeConditions", "  ✗ NO STEPS - Badge not shown")
                        !allStepsPassed -> android.util.Log.d("BadgeConditions", "  ✗ NOT ALL STEPS PASSED ($passedSteps/$totalSteps) - Badge not shown")
                        !hasValidId || !hasValidTitle -> android.util.Log.d("BadgeConditions", "  ✗ INVALID DATA - Badge not shown")
                    }
                }
            }

            android.util.Log.d("BadgeConditions", "Total completed courses (badges to show): ${completedCourses.size}")
            android.util.Log.d("BadgeConditions", "Web matching logic: Showing courses where ALL steps are passed")
            android.util.Log.d("BadgeConditions", "========== BADGE LOADING COMPLETE ==========")

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

    suspend fun getTeamById(teamId: String): org.ole.planet.myplanet.model.dto.Team? = teamsRepository.getTeamById(teamId)
}

data class CourseCompletion(val courseId: String?, val courseTitle: String?)

sealed class NetworkStatus {
    object Disconnected : NetworkStatus()
    object Connecting : NetworkStatus()
    object Connected : NetworkStatus()
}
