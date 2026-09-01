package org.ole.planet.myplanet.ui.dashboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.TeamNotificationInfo
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.SyncRepository
import org.ole.planet.myplanet.repository.SyncUiState
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NotificationConfig
import org.ole.planet.myplanet.utils.RetryUtils

data class DashboardUiState(
    val unreadNotifications: Int = 0,
    val newNotifications: List<NotificationConfig> = emptyList(),
    val library: List<MyLibrary> = emptyList(),
    val courses: List<MyCourse> = emptyList(),
    val teams: List<MyTeam> = emptyList(),
    val users: List<UserEntity> = emptyList(),
    val offlineLogins: Int = 0,
    val fullName: String? = null,
)

data class ChallengeDialogData(
    val voiceCount: Int,
    val courseStatus: String,
    val allVoiceCount: Int,
    val hasUnfinishedSurvey: Boolean,
    val hasValidSync: Boolean
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val application: Application,
    private val userRepository: UserRepository,
    private val resourcesRepository: ResourcesRepository,
    private val coursesRepository: CoursesRepository,
    private val teamsRepository: TeamsRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val notificationsRepository: NotificationsRepository,
    private val surveysRepository: SurveysRepository,
    private val progressRepository: ProgressRepository,
    private val voicesRepository: VoicesRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val syncRepository: SyncRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _surveyNavigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val surveyNavigationEvent: SharedFlow<String> = _surveyNavigationEvent.asSharedFlow()

    private val _taskNavigationEvent = MutableSharedFlow<Triple<String, String, String>>(extraBufferCapacity = 1)
    val taskNavigationEvent: SharedFlow<Triple<String, String, String>> = _taskNavigationEvent.asSharedFlow()

    private val _syncKeyIdEvent = MutableSharedFlow<SyncUiState>(extraBufferCapacity = 1)
    val syncKeyIdEvent: SharedFlow<SyncUiState> = _syncKeyIdEvent.asSharedFlow()

    private var syncJob: Job? = null

    fun syncKeyId(role: String?) {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            _syncKeyIdEvent.emit(SyncUiState.Loading)
            val result = syncRepository.syncDashboardKeyId(role)
            _syncKeyIdEvent.emit(result)
        }
    }

    private val _joinRequestNavigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val joinRequestNavigationEvent: SharedFlow<String> = _joinRequestNavigationEvent.asSharedFlow()

    private val _challengeDialogEvent = MutableSharedFlow<ChallengeDialogData>(extraBufferCapacity = 1)
    val challengeDialogEvent: SharedFlow<ChallengeDialogData> = _challengeDialogEvent.asSharedFlow()

    private var libraryJob: Job? = null
    private var coursesJob: Job? = null
    private var teamsJob: Job? = null
    private var profileJob: Job? = null

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
        val resourceCount = resourcesRepository.countLibrariesNeedingUpdate(userId)
        notificationsRepository.updateResourceNotification(userId, resourceCount)
    }

    suspend fun getUnreadNotificationsSize(userId: String?, isAdmin: Boolean = false): Int {
        return notificationsRepository.getUnreadCount(userId, isAdmin)
    }

    suspend fun getTeamNotifications(teamIds: List<String>, userId: String): Map<String, TeamNotificationInfo> {
        return notificationsRepository.getTeamNotifications(teamIds, userId)
    }

    fun loadUserContent(userId: String?) {
        if (userId == null) return

        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            resourcesRepository.getMyLibraryFlow(userId)
                .flowOn(dispatcherProvider.io)
                .distinctUntilChanged { old, new ->
                    if (old.size != new.size) return@distinctUntilChanged false
                    for (i in old.indices) {
                        if (old[i]._id != new[i]._id || old[i]._rev != new[i]._rev) return@distinctUntilChanged false
                    }
                    true
                }
                .collect { myLibrary ->
                    _uiState.update { it.copy(library = myLibrary) }
                }
        }

        coursesJob?.cancel()
        coursesJob = viewModelScope.launch {
            coursesRepository.getMyCoursesFlow(userId)
                .flowOn(dispatcherProvider.io)
                .collect { courses ->
                    _uiState.update { it.copy(courses = courses) }
                }
        }

        teamsJob?.cancel()
        teamsJob = viewModelScope.launch {
            teamsRepository.getMyTeamsFlow(userId)
                .flowOn(dispatcherProvider.io)
                .collect { teams ->
                    _uiState.update { it.copy(teams = teams) }
                }
        }

        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            val profile = userRepository.getDashboardProfile(userId)

            _uiState.update { it.copy(fullName = profile.fullName, offlineLogins = profile.offlineLogins) }
        }
    }

    suspend fun getTeamType(teamId: String): String? {
        return teamsRepository.getTeamType(teamId)
    }

    suspend fun getLibraryListForUser(userId: String?): List<MyLibrary> {
        return resourcesRepository.getLibraryListForUser(userId)
    }

    fun loadUsers() {
        viewModelScope.launch {
            val users = userRepository.getUsersSortedBy("joinDate", true)
            _uiState.update { it.copy(users = users) }
        }
    }

    fun dashboardDataFlow(userId: String?): Flow<Unit> {
        return merge(
            resourcesRepository.getRecentResources(userId ?: "").map {},
            resourcesRepository.getPendingDownloads(userId ?: "").map {},
            submissionsRepository.getPendingSurveysFlow(userId).map {},
            teamsRepository.getTasksFlow(userId).map {}
        )
    }

    fun handleTaskNavigation(taskId: String) {
        viewModelScope.launch {
            val teamData = teamsRepository.getTaskTeamInfo(taskId)
            if (teamData != null) {
                _taskNavigationEvent.emit(teamData)
            }
        }
    }

    fun handleJoinRequestNavigation(requestId: String) {
        viewModelScope.launch {
            val teamId = teamsRepository.getJoinRequestTeamId(requestId)
            if (teamId != null) {
                _joinRequestNavigationEvent.emit(teamId)
            }
        }
    }

    fun refreshNotificationsWithRetry(userId: String, maxRetries: Int = 2) {
        viewModelScope.launch {
            val unreadCount = RetryUtils.retry(maxAttempts = maxRetries, delayMs = 300L) {
                notificationsRepository.refresh()
                getUnreadNotificationsSize(userId)
            }
            if (unreadCount != null) {
                setUnreadNotifications(unreadCount)
            }
        }
    }

    fun markNotificationAsRead(notificationId: String, userId: String?) {
        viewModelScope.launch {
            try {
                notificationsRepository.markNotificationAsRead(notificationId, userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshNotificationsBadge(userId: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(100.milliseconds)
            try {
                notificationsRepository.refresh()
                val unreadCount = getUnreadNotificationsSize(userId)
                setUnreadNotifications(unreadCount)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleSurveyNavigation(surveyId: String) {
        viewModelScope.launch {
            val survey = surveysRepository.getSurvey(surveyId)
            survey?.id?.let { id ->
                _surveyNavigationEvent.emit(id)
            }
        }
    }

    fun evaluateChallengeDialog(
        userId: String?,
        isGuest: Boolean,
        validUrls: List<String>,
        serverUrl: String
    ) {
        val startTime = 1730419200000
        val endTime = 1734307200000
        val courseId = "4e6b78800b6ad18b4e8b0e1e38a98cac"

        viewModelScope.launch {
            try {
                val dialogData = coroutineScope {
                    val courseDataDeferred = async { progressRepository.fetchCourseData(userId) }
                    val voiceCountDeferred = async { voicesRepository.getCommunityVoiceDateCount(startTime, endTime, userId) }
                    val allVoiceCountDeferred = async { voicesRepository.getCommunityVoiceDateCount(startTime, endTime, null) }
                    val courseNameDeferred = async { coursesRepository.getCourseTitleById(courseId) }
                    val hasUnfinishedSurveyDeferred = async { submissionsRepository.hasPendingSurvey(courseId, userId) }

                    val courseData = courseDataDeferred.await()
                    val voiceCount = voiceCountDeferred.await()
                    val allVoiceCount = allVoiceCountDeferred.await()
                    val courseName = courseNameDeferred.await()
                    val hasUnfinishedSurvey = hasUnfinishedSurveyDeferred.await()

                    val progress = progressRepository.findProgressForCourse(courseData, courseId)

                    val today = LocalDate.now()
                    val endDate = LocalDate.of(2025, 1, 16)
                    val shouldPrompt = today.isAfter(LocalDate.of(2024, 11, 30)) &&
                            today.isBefore(endDate) &&
                            serverUrl in validUrls

                    if (!isGuest && shouldPrompt) {
                        val courseStatus = getCourseStatusString(progress, courseName)
                        val prereqsMet = courseStatus.contains("terminado", ignoreCase = true) && voiceCount >= 5
                        var hasValidSync = false
                        if (prereqsMet) {
                            hasValidSync = progressRepository.hasUserCompletedSync(userId ?: "")
                        }

                        ChallengeDialogData(
                            voiceCount = voiceCount,
                            courseStatus = courseStatus,
                            allVoiceCount = allVoiceCount,
                            hasUnfinishedSurvey = hasUnfinishedSurvey,
                            hasValidSync = hasValidSync
                        )
                    } else {
                        null
                    }
                }

                if (dialogData != null) {
                    _challengeDialogEvent.emit(dialogData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getCourseStatusString(progress: JsonObject?, courseName: String?): String {
        return if (progress != null) {
            val max = JsonUtils.getInt("max", progress)
            val current = JsonUtils.getInt("current", progress)
            if (current == max) {
                application.getString(org.ole.planet.myplanet.R.string.course_completed, courseName)
            } else {
                application.getString(org.ole.planet.myplanet.R.string.course_in_progress, courseName, current, max)
            }
        } else {
            application.getString(org.ole.planet.myplanet.R.string.course_not_started, courseName)
        }
    }

    suspend fun checkAndCreateNewNotifications(userId: String?, isAdmin: Boolean = false) {
        try {
            updateResourceNotification(userId)
            val unreadCount = getUnreadNotificationsSize(userId, isAdmin)
            _uiState.update { it.copy(unreadNotifications = unreadCount) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearNewNotifications() {
        _uiState.update { it.copy(newNotifications = emptyList()) }
    }
}
