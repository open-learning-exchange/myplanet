package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.UserRepository
import java.util.Calendar

data class TeamWithNotification(
    val team: RealmMyTeam,
    val hasUnreadChat: Boolean,
    val hasPendingTask: Boolean,
)

data class DashboardData(
    val user: RealmUserModel?,
    val library: List<RealmMyLibrary>,
    val courses: List<RealmMyCourse>,
    val teams: List<TeamWithNotification>,
    val myLife: List<RealmMyLife>,
    val offlineVisits: Int,
)

data class DashboardUiState(
    val unreadNotifications: Int = 0,
    val dashboardData: DashboardData? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository,
    private val teamRepository: TeamRepository,
    private val lifeRepository: LifeRepository,
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

    fun loadDashboardData(userId: String) {
        userContentJob?.cancel()
        userContentJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val user = userRepository.getUserModel(userId)
                val library = libraryRepository.getMyLibrary(userId)
                val courses = courseRepository.getMyCourses(userId)
                val teams = teamRepository.getMyTeams(userId)
                var myLife = lifeRepository.getMyLifeByUserId(userId)
                if (myLife.isEmpty()) {
                    lifeRepository.setUpMyLife(userId)
                    myLife = lifeRepository.getMyLifeByUserId(userId)
                }
                val offlineVisits = userRepository.getOfflineVisits(userId)

                val teamIds = teams.mapNotNull { it._id }
                val notifications = notificationRepository.getNotificationsByParentIds(teamIds)
                    .associateBy { it.parentId }
                val chatCounts = notificationRepository.getChatCounts(teamIds)
                val current = Calendar.getInstance().timeInMillis
                val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                val tasks = teamRepository.getTasks(userId, current, tomorrow)
                    .groupBy { it.teamId }

                val teamsWithNotifications = teams.map { team ->
                    val notification = notifications[team._id]
                    val chatCount = chatCounts[team._id] ?: 0L
                    val hasUnreadChat = (notification?.lastCount ?: 0) < chatCount
                    val hasPendingTask = tasks.containsKey(team._id)
                    TeamWithNotification(team, hasUnreadChat, hasPendingTask)
                }

                val dashboardData = DashboardData(
                    user = user,
                    library = library,
                    courses = courses,
                    teams = teamsWithNotifications,
                    myLife = myLife,
                    offlineVisits = offlineVisits,
                )
                _uiState.update { it.copy(dashboardData = dashboardData) }
            }
        }
    }
}
