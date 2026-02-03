package org.ole.planet.myplanet.ui.dashboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Sort
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamNotificationInfo
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.NotificationConfig
import org.ole.planet.myplanet.utils.NotificationUtils

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
    private val activitiesRepository: ActivitiesRepository,
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
        val resourceCount = resourcesRepository.countLibrariesNeedingUpdate(userId)
        notificationsRepository.updateResourceNotification(userId, resourceCount)
    }

    suspend fun createNotificationIfMissing(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    ) {
        notificationsRepository.createNotificationIfMissing(type, message, relatedId, userId)
    }

    suspend fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        return submissionsRepository.getPendingSurveys(userId)
    }

    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> {
        return submissionsRepository.getSurveyTitlesFromSubmissions(submissions)
    }

    suspend fun getSurveySubmissionCount(userId: String?): Int {
        return surveysRepository.getSurveySubmissionCount(userId)
    }

    suspend fun getUnreadNotificationsSize(userId: String?): Int {
        return notificationsRepository.getUnreadCount(userId)
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
            val libraryDeferred = async {
                resourcesRepository.getMyLibrary(userId)
            }

            val coursesFlowJob = launch {
                coursesRepository.getMyCoursesFlow(userId).collect { courses ->
                    _uiState.update { it.copy(courses = courses) }
                }
            }

            val teamsFlowJob = launch {
                teamsRepository.getMyTeamsFlow(userId).collect { teams ->
                    _uiState.update { it.copy(teams = teams) }
                }
            }

            launch {
                val user = userRepository.getUserById(userId)
                val userName = user?.name
                val fullName = user?.getFullName()?.takeIf { it.trim().isNotBlank() } ?: user?.name
                _uiState.update { it.copy(fullName = fullName) }

                if (userName != null) {
                    activitiesRepository.getOfflineLogins(userName).collect { logins ->
                        _uiState.update { it.copy(offlineLogins = logins.size) }
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

    fun loadUsers() {
        viewModelScope.launch {
            val users = userRepository.getUsersSortedBy("joinDate", Sort.DESCENDING)
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

    suspend fun checkAndCreateNewNotifications(userId: String?) = withContext(Dispatchers.IO) {
        var unreadCount = 0
        val newNotifications = mutableListOf<NotificationConfig>()

        try {
            updateResourceNotification(userId)

            val taskData = teamsRepository.getTaskNotifications(userId)
            val joinRequestData = teamsRepository.getJoinRequestNotifications(userId)

            val pendingSurveys = submissionsRepository.getPendingSurveys(userId)
            val surveyTitles = submissionsRepository.getSurveyTitlesFromSubmissions(pendingSurveys)
            val storageRatio = FileUtils.totalAvailableMemoryRatio(application).toInt()
            val joinRequestTemplate = application.getString(R.string.user_requested_to_join_team)

            val realmNotifications = notificationsRepository.checkAndCreateNotifications(
                userId,
                taskData,
                joinRequestData,
                joinRequestTemplate,
                storageRatio,
                surveyTitles
            )

            val createdNotifications = realmNotifications.mapNotNull {
                createNotificationConfigFromDatabase(it)
            }
            newNotifications.addAll(createdNotifications)

            unreadCount = getUnreadNotificationsSize(userId)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val groupedNotifications = newNotifications.groupBy { it.type }
        val finalNotifications = mutableListOf<NotificationConfig>()

        groupedNotifications.forEach { (type, notifications) ->
            when {
                notifications.size == 1 -> {
                    finalNotifications.add(notifications.first())
                }
                notifications.size > 1 -> {
                    val summaryConfig = NotificationUtils.createSummaryNotification(type, notifications.size)
                    finalNotifications.add(summaryConfig)
                }
            }
        }

        _uiState.update {
            it.copy(
                unreadNotifications = unreadCount,
                newNotifications = finalNotifications
            )
        }
    }

    fun clearNewNotifications() {
        _uiState.update { it.copy(newNotifications = emptyList()) }
    }

    private fun createNotificationConfigFromDatabase(dbNotification: RealmNotification): NotificationConfig? {
        return when (dbNotification.type.lowercase()) {
            "survey" -> NotificationUtils.createSurveyNotification(
                dbNotification.id,
                dbNotification.message
            ).copy(
                extras = mapOf("surveyId" to (dbNotification.relatedId ?: dbNotification.id))
            )
            "task" -> {
                val parts = dbNotification.message.split(" ")
                val taskTitle = parts.dropLast(3).joinToString(" ")
                val deadline = parts.takeLast(3).joinToString(" ")
                NotificationUtils.createTaskNotification(dbNotification.id, taskTitle, deadline).copy(
                    extras = mapOf("taskId" to (dbNotification.relatedId ?: dbNotification.id))
                )
            }
            "resource" -> NotificationUtils.createResourceNotification(
                dbNotification.id,
                dbNotification.message.toIntOrNull() ?: 0
            )
            "storage" -> {
                val storageValue = dbNotification.message.replace("%", "").toIntOrNull() ?: 0
                NotificationUtils.createStorageWarningNotification(storageValue, dbNotification.id)
            }
            "join_request" -> NotificationUtils.createJoinRequestNotification(
                dbNotification.id,
                "New Request",
                dbNotification.message
            ).copy(
                extras = mapOf("requestId" to (dbNotification.relatedId ?: dbNotification.id), "teamName" to dbNotification.message)
            )
            else -> null
        }
    }
}
