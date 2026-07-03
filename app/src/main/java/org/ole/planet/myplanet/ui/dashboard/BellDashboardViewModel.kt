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
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.utils.NetworkUtils.isNetworkConnectedFlow
import org.ole.planet.myplanet.utils.StreakUtils
import org.ole.planet.myplanet.utils.TimeProvider

data class LearningSummary(
    val streakDays: Int,
    val inProgressCourses: Int,
    val completedCourses: Int
)

@HiltViewModel
class BellDashboardViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val teamsRepository: TeamsRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Disconnected)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _completedCourses = MutableStateFlow<List<CourseCompletion>>(emptyList())
    val completedCourses: StateFlow<List<CourseCompletion>> = _completedCourses.asStateFlow()

    private val _learningSummary = MutableStateFlow<LearningSummary?>(null)
    val learningSummary: StateFlow<LearningSummary?> = _learningSummary.asStateFlow()

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

    fun loadLearningSummary(userId: String, userName: String?) {
        viewModelScope.launch {
            val completed = progressRepository.getCompletedCourses(userId)
            _completedCourses.value = completed

            val activityTimes = if (userName.isNullOrBlank()) {
                emptyList()
            } else {
                activitiesRepository.getLearningActivityTimes(userName)
            }
            val streak = StreakUtils.calculateDayStreak(activityTimes, timeProvider.now())

            val completedIds = completed.mapNotNull { it.courseId }.toSet()
            val inProgress = progressRepository.getProgressRecords(userId)
                .mapNotNull { it.courseId }
                .toSet()
                .count { it !in completedIds }

            _learningSummary.value = LearningSummary(streak, inProgress, completed.size)
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

sealed class NetworkStatus {
    object Disconnected : NetworkStatus()
    object Connecting : NetworkStatus()
    object Connected : NetworkStatus()
}
