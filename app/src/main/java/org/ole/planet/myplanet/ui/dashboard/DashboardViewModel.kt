package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.model.TeamNotificationInfo
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.model.RealmTeamTask

data class DashboardUiState(
    val unreadNotifications: Int = 0,
    val library: List<RealmMyLibrary> = emptyList(),
    val courses: List<RealmMyCourse> = emptyList(),
    val teams: List<RealmMyTeam> = emptyList(),
    val teamNotifications: Map<String, TeamNotificationInfo> = emptyMap(),
    val upcomingTasks: List<RealmTeamTask> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository,
    private val teamRepository: TeamRepository,
    private val submissionRepository: SubmissionRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var userContentJob: Job? = null

    fun setUnreadNotifications(count: Int) {
        _uiState.update { it.copy(unreadNotifications = count) }
    }
    fun calculateIndividualProgress(voiceCount: Int, hasUnfinishedSurvey: Boolean): Int {
        val earnedDollarsVoice = minOf(voiceCount, 5) * 2
        val earnedDollarsSurvey = if (!hasUnfinishedSurvey) 1 else 0
        val total = earnedDollarsVoice + earnedDollarsSurvey
        return total.coerceAtMost(500)
    }

    fun calculateCommunityProgress(allVoiceCount: Int, hasUnfinishedSurvey: Boolean): Int {
        val earnedDollarsVoice = minOf(allVoiceCount, 5) * 2
        val earnedDollarsSurvey = if (!hasUnfinishedSurvey) 1 else 0
        val total = earnedDollarsVoice + earnedDollarsSurvey
        return total.coerceAtMost(11)
    }

    suspend fun updateResourceNotification(userId: String?) {
        val resourceCount = libraryRepository.countLibrariesNeedingUpdate(userId)
        notificationRepository.updateResourceNotification(userId, resourceCount)
    }

    suspend fun createNotificationIfMissing(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    ) {
        notificationRepository.createNotificationIfMissing(type, message, relatedId, userId)
    }

    suspend fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        return submissionRepository.getPendingSurveys(userId)
    }

    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> {
        return submissionRepository.getSurveyTitlesFromSubmissions(submissions)
    }

    suspend fun getUnreadNotificationsSize(userId: String?): Int {
        return notificationRepository.getUnreadCount(userId)
    }

    fun loadUserContent(userId: String?) {
        if (userId == null) return
        userContentJob?.cancel()
        userContentJob = viewModelScope.launch {
            launch {
                val myLibrary = libraryRepository.getMyLibrary(userId)
                _uiState.update { it.copy(library = myLibrary) }
            }

            launch {
                courseRepository.getMyCoursesFlow(userId).collect { courses ->
                    _uiState.update { it.copy(courses = courses) }
                }
            }

            launch {
                teamRepository.getMyTeamsFlow(userId).collect { teams ->
                    val teamNotifications = getTeamNotificationInfo(teams)
                    _uiState.update { it.copy(teams = teams, teamNotifications = teamNotifications) }
                }
            }

            launch {
                val upcomingTasks = getUpcomingTasks(userId)
                _uiState.update { it.copy(upcomingTasks = upcomingTasks) }
            }
        }
    }

    private suspend fun getTeamNotificationInfo(teams: List<RealmMyTeam>): Map<String, TeamNotificationInfo> {
        if (teams.isEmpty()) return emptyMap()
        val teamIds = teams.mapNotNull { it._id }
        if (teamIds.isEmpty()) return emptyMap()

        val notifications = notificationRepository.getNotificationsByParentIds(teamIds).associateBy { it.parentId }
        val chatCounts = teamRepository.getTeamChatCounts(teamIds)

        return teamIds.associateWith { teamId ->
            TeamNotificationInfo(
                notification = notifications[teamId],
                chatCount = chatCounts[teamId] ?: 0L
            )
        }
    }

    private suspend fun getUpcomingTasks(userId: String?): List<RealmTeamTask> {
        if (userId.isNullOrBlank()) return emptyList()
        return teamRepository.getUpcomingTasks(userId)
    }
}
