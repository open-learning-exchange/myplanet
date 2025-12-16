package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Sort
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTeamNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.TeamNotificationInfo
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.SurveyRepository
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.ActivityRepository

data class DashboardUiState(
    val unreadNotifications: Int = 0,
    val library: List<RealmMyLibrary> = emptyList(),
    val courses: List<RealmMyCourse> = emptyList(),
    val teams: List<RealmMyTeam> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository,
    private val teamRepository: TeamRepository,
    private val submissionRepository: SubmissionRepository,
    private val notificationRepository: NotificationRepository,
    private val surveyRepository: SurveyRepository,
    private val activityRepository: ActivityRepository,
    private val databaseService: DatabaseService
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

    suspend fun getSurveySubmissionCount(userId: String?): Int {
        return surveyRepository.getSurveySubmissionCount(userId)
    }

    suspend fun getUnreadNotificationsSize(userId: String?): Int {
        return notificationRepository.getUnreadCount(userId)
    }

    suspend fun getTeamNotificationInfo(teamId: String, userId: String): TeamNotificationInfo {
        return databaseService.withRealmAsync { realm ->
            val current = System.currentTimeMillis()
            val tomorrow = Calendar.getInstance()
            tomorrow.add(Calendar.DAY_OF_YEAR, 1)

            val notification = realm.where(RealmTeamNotification::class.java)
                .equalTo("parentId", teamId)
                .equalTo("type", "chat")
                .findFirst()

            val chatCount = realm.where(RealmNews::class.java)
                .equalTo("viewableBy", "teams")
                .equalTo("viewableId", teamId)
                .count()

            val hasChat = notification != null && notification.lastCount < chatCount

            val tasks = realm.where(RealmTeamTask::class.java)
                .equalTo("assignee", userId)
                .between("deadline", current, tomorrow.timeInMillis)
                .findAll()

            val hasTask = tasks.isNotEmpty()

            TeamNotificationInfo(hasTask, hasChat)
        }
    }

    suspend fun getTeamNotifications(teamIds: List<String>, userId: String): Map<String, TeamNotificationInfo> {
        return databaseService.withRealmAsync { realm ->
            if (teamIds.isEmpty()) {
                return@withRealmAsync emptyMap()
            }
            val notificationMap = mutableMapOf<String, TeamNotificationInfo>()

            // 1. Fetch all relevant notifications in a single query
            val notificationQuery = realm.where(RealmTeamNotification::class.java).equalTo("type", "chat")
            notificationQuery.beginGroup()
            teamIds.forEachIndexed { index, id ->
                if (index > 0) notificationQuery.or()
                notificationQuery.equalTo("parentId", id)
            }
            notificationQuery.endGroup()
            val notificationsResult = notificationQuery.findAll()
            val notificationsById = mutableMapOf<String, RealmTeamNotification>()
            notificationsResult.forEach {
                it.parentId?.let { parentId ->
                    notificationsById[parentId] = it
                }
            }


            // 2. Fetch all relevant chat counts in a single query
            val chatQuery = realm.where(RealmNews::class.java).equalTo("viewableBy", "teams")
            chatQuery.beginGroup()
            teamIds.forEachIndexed { index, id ->
                if (index > 0) chatQuery.or()
                chatQuery.equalTo("viewableId", id)
            }
            chatQuery.endGroup()
            val chatsResult = chatQuery.findAll()
            val chatCountsById = mutableMapOf<String, Long>()
            chatsResult.forEach {
                it.viewableId?.let { viewableId ->
                    val currentCount = chatCountsById[viewableId] ?: 0
                    chatCountsById[viewableId] = currentCount + 1
                }
            }


            // 3. Fetch all relevant tasks once
            val current = System.currentTimeMillis()
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val tasks = realm.where(RealmTeamTask::class.java)
                .equalTo("assignee", userId)
                .between("deadline", current, tomorrow.timeInMillis)
                .findAll()
            val hasTask = tasks.isNotEmpty()

            // 4. Combine the results in memory
            for (teamId in teamIds) {
                val notification = notificationsById[teamId]
                val chatCount = chatCountsById[teamId] ?: 0L
                val hasChat = notification != null && notification.lastCount < chatCount
                notificationMap[teamId] = TeamNotificationInfo(hasTask, hasChat)
            }
            notificationMap
        }
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
                    _uiState.update { it.copy(teams = teams) }
                }
            }
        }
    }

    suspend fun getUsersSortedByDate() = userRepository.getUsersSortedBy("joinDate", Sort.DESCENDING)

    suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity> {
        return activityRepository.getOfflineActivities(userName, type)
    }

    suspend fun getPrivateLibraryAfterDate(date: Long): List<RealmMyLibrary> {
        return libraryRepository.getPrivateLibraryAfterDate(date)
    }
}
