package org.ole.planet.myplanet.ui.dashboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Sort
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamNotificationInfo
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NotificationConfig

data class DashboardUiState(
    val unreadNotifications: Int = 0,
    val newNotifications: List<NotificationConfig> = emptyList(),
    val library: List<RealmMyLibrary> = emptyList(),
    val courses: List<RealmMyCourse> = emptyList(),
    val teams: List<RealmMyTeam> = emptyList(),
    val users: List<RealmUser> = emptyList(),
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
    private val lifeRepository: LifeRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val notificationsRepository: NotificationsRepository,
    private val surveysRepository: SurveysRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val progressRepository: org.ole.planet.myplanet.repository.ProgressRepository,
    private val voicesRepository: org.ole.planet.myplanet.repository.VoicesRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _surveyNavigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val surveyNavigationEvent: SharedFlow<String> = _surveyNavigationEvent.asSharedFlow()

    private val _taskNavigationEvent = MutableSharedFlow<Triple<String, String, String>>(extraBufferCapacity = 1)
    val taskNavigationEvent: SharedFlow<Triple<String, String, String>> = _taskNavigationEvent.asSharedFlow()

    private val _joinRequestNavigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val joinRequestNavigationEvent: SharedFlow<String> = _joinRequestNavigationEvent.asSharedFlow()

    private val _challengeDialogEvent = MutableSharedFlow<ChallengeDialogData>(extraBufferCapacity = 1)
    val challengeDialogEvent: SharedFlow<ChallengeDialogData> = _challengeDialogEvent.asSharedFlow()

    private var userContentJob: Job? = null

    suspend fun loadVisibleMyLifeItems(userId: String?, defaultItems: List<RealmMyLife>): List<RealmMyLife> {
        return withContext(dispatcherProvider.io) {
            val allForUser = lifeRepository.getMyLifeByUserId(userId)
            if (allForUser.isEmpty()) {
                lifeRepository.seedMyLifeIfEmpty(userId, defaultItems)
                lifeRepository.getMyLifeByUserId(userId).filter { it.isVisible }
            } else {
                allForUser.filter { it.isVisible }
            }
        }
    }

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

    suspend fun getSurveySubmissionCount(userId: String?): Int {
        return surveysRepository.getSurveySubmissionCount(userId)
    }

    suspend fun getUnreadNotificationsSize(userId: String?, isAdmin: Boolean = false): Int {
        return notificationsRepository.getUnreadCount(userId, isAdmin)
    }

    suspend fun getTeamNotificationInfo(teamId: String, userId: String): TeamNotificationInfo {
        return notificationsRepository.getTeamNotificationInfo(teamId, userId)
    }

    suspend fun getTeamNotifications(teamIds: List<String>, userId: String): Map<String, TeamNotificationInfo> {
        return notificationsRepository.getTeamNotifications(teamIds, userId)
    }

    fun loadUserContent(userId: String?) {
        if (userId == null) return
        userContentJob?.cancel()
        userContentJob = viewModelScope.launch {
            val libraryDeferred = async(dispatcherProvider.io) {
                resourcesRepository.getMyLibrary(userId)
            }

            val coursesFlowJob = launch(dispatcherProvider.io) {
                coursesRepository.getMyCoursesFlow(userId).collect { courses ->
                    withContext(dispatcherProvider.main) {
                        _uiState.update { it.copy(courses = courses) }
                    }
                }
            }

            val teamsFlowJob = launch(dispatcherProvider.io) {
                teamsRepository.getMyTeamsFlow(userId).collect { teams ->
                    withContext(dispatcherProvider.main) {
                        _uiState.update { it.copy(teams = teams) }
                    }
                }
            }

            launch(dispatcherProvider.io) {
                val user = userRepository.getUserById(userId)
                val userName = user?.name
                val fullName = user?.getFullName()?.takeIf { it.trim().isNotBlank() } ?: user?.name
                withContext(dispatcherProvider.main) {
                    _uiState.update { it.copy(fullName = fullName) }
                }

                if (userName != null) {
                    activitiesRepository.getOfflineLogins(userName).collect { logins ->
                        withContext(dispatcherProvider.main) {
                            _uiState.update { it.copy(offlineLogins = logins.size) }
                        }
                    }
                }
            }

            val myLibrary = libraryDeferred.await()
            _uiState.update { it.copy(library = myLibrary) }
        }
    }

    suspend fun getOfflineActivities(userName: String, type: String): List<RealmOfflineActivity> {
        return activitiesRepository.getOfflineActivities(userName, type)
    }

    suspend fun getLibraryForSelectedUser(userId: String): List<RealmMyLibrary> {
        return resourcesRepository.getLibraryForSelectedUser(userId)
    }

    suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary> {
        return resourcesRepository.getLibraryListForUser(userId)
    }

    fun loadUsers() {
        viewModelScope.launch {
            val users = withContext(dispatcherProvider.io) {
                userRepository.getUsersSortedBy("joinDate", Sort.DESCENDING)
            }
            _uiState.update { it.copy(users = users) }
        }
    }

    suspend fun dashboardDataFlow(userId: String?): Flow<Unit> {
        return merge(
            resourcesRepository.getRecentResources(userId ?: "").map {},
            resourcesRepository.getPendingDownloads(userId ?: "").map {},
            submissionsRepository.getPendingSurveysFlow(userId).map {},
            teamsRepository.getTasksFlow(userId).map {}
        )
    }

    fun handleTaskNavigation(taskId: String) {
        viewModelScope.launch {
            val teamData = withContext(dispatcherProvider.io) {
                teamsRepository.getTaskTeamInfo(taskId)
            }
            if (teamData != null) {
                _taskNavigationEvent.emit(teamData)
            }
        }
    }

    fun handleJoinRequestNavigation(requestId: String) {
        viewModelScope.launch {
            val teamId = withContext(dispatcherProvider.io) {
                teamsRepository.getJoinRequestTeamId(requestId)
            }
            if (teamId != null) {
                _joinRequestNavigationEvent.emit(teamId)
            }
        }
    }

    fun refreshNotificationsWithRetry(userId: String, maxRetries: Int = 2) {
        viewModelScope.launch {
            var lastException: Exception? = null
            repeat(maxRetries) { attempt ->
                try {
                    val unreadCount = withContext(dispatcherProvider.io) {
                        notificationsRepository.refresh()
                        getUnreadNotificationsSize(userId)
                    }
                    setUnreadNotifications(unreadCount)
                    return@launch
                } catch (e: Exception) {
                    lastException = e
                    e.printStackTrace()
                    if (attempt < maxRetries - 1) {
                        kotlinx.coroutines.delay(300)
                    }
                }
            }
            lastException?.printStackTrace()
        }
    }

    fun markNotificationAsRead(notificationId: String, userId: String?) {
        viewModelScope.launch {
            try {
                withContext(dispatcherProvider.io) {
                    notificationsRepository.markNotificationAsRead(notificationId, userId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshNotificationsBadge(userId: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            try {
                val unreadCount = withContext(dispatcherProvider.io) {
                    notificationsRepository.refresh()
                    getUnreadNotificationsSize(userId)
                }
                setUnreadNotifications(unreadCount)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleSurveyNavigation(surveyId: String) {
        viewModelScope.launch {
            val survey = withContext(dispatcherProvider.io) {
                surveysRepository.getSurvey(surveyId)
            }
            if (survey != null && survey.id != null) {
                _surveyNavigationEvent.emit(survey.id!!)
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
                val courseData = withContext(dispatcherProvider.io) { progressRepository.fetchCourseData(userId) }
                val uniqueDates = withContext(dispatcherProvider.io) { voicesRepository.getCommunityVoiceDates(startTime, endTime, userId) }
                val allUniqueDates = withContext(dispatcherProvider.io) { voicesRepository.getCommunityVoiceDates(startTime, endTime, null) }
                val courseName = withContext(dispatcherProvider.io) { coursesRepository.getCourseTitleById(courseId) }
                val hasUnfinishedSurvey = withContext(dispatcherProvider.io) { hasPendingSurvey(courseId, userId) }

                val progress = org.ole.planet.myplanet.ui.courses.CoursesProgressFragment.getCourseProgress(courseData, courseId)

                val today = java.time.LocalDate.now()
                val endDate = java.time.LocalDate.of(2025, 1, 16)
                val shouldPrompt = today.isAfter(java.time.LocalDate.of(2024, 11, 30)) &&
                        today.isBefore(endDate) &&
                        serverUrl in validUrls

                if (!isGuest && shouldPrompt) {
                    val courseStatus = getCourseStatusString(progress, courseName)
                    val voiceCount = uniqueDates.size
                    val prereqsMet = courseStatus.contains("terminado", ignoreCase = true) && voiceCount >= 5
                    var hasValidSync = false
                    if (prereqsMet) {
                        hasValidSync = withContext(dispatcherProvider.io) { progressRepository.hasUserCompletedSync(userId ?: "") }
                    }
                    _challengeDialogEvent.emit(
                        ChallengeDialogData(
                            voiceCount = uniqueDates.size,
                            courseStatus = courseStatus,
                            allVoiceCount = allUniqueDates.size,
                            hasUnfinishedSurvey = hasUnfinishedSurvey,
                            hasValidSync = hasValidSync
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun hasPendingSurvey(courseId: String, userId: String?): Boolean {
        val surveys = submissionsRepository.getSurveysByCourseId(courseId)
        for (survey in surveys) {
            if (!submissionsRepository.hasSubmission(survey.id, survey.courseId, userId, "survey")) {
                return true
            }
        }
        return false
    }

    private fun getCourseStatusString(progress: com.google.gson.JsonObject?, courseName: String?): String {
        return if (progress != null) {
            val max = progress.get("max").asInt
            val current = progress.get("current").asInt
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
            val unreadCount = withContext(dispatcherProvider.io) {
                updateResourceNotification(userId)
                getUnreadNotificationsSize(userId, isAdmin)
            }
            _uiState.update { it.copy(unreadNotifications = unreadCount) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearNewNotifications() {
        _uiState.update { it.copy(newNotifications = emptyList()) }
    }
}
