package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.JoinRequestNotification
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utilities.FileUtils
import android.content.Intent
import org.ole.planet.myplanet.ui.team.TeamPageConfig
import org.ole.planet.myplanet.utilities.NotificationUtils

sealed class UserState {
    object Authenticated : UserState()
    object Guest : UserState()
    object GuestTrialExpired : UserState()
    object SessionExpired : UserState()
    object Inactive : UserState()
}

sealed class NavigationEvent {
    data class ToSurvey(val surveyId: String) : NavigationEvent()
    data class ToTask(
        val teamId: String,
        val teamName: String,
        val teamType: String,
    ) : NavigationEvent()

    data class ToJoinRequest(val teamId: String) : NavigationEvent()
    data class ToResource(val resourceId: String) : NavigationEvent()
    object ToResources : NavigationEvent()
    object ToSettings : NavigationEvent()
    object ToNotifications : NavigationEvent()
    data class OpenSurvey(
        val surveyId: String?,
        val isEdit: Boolean,
        val isPending: Boolean,
        val savedAns: String
    ) : NavigationEvent()
}

data class DashboardUiState(
    val userState: UserState = UserState.Authenticated,
    val unreadNotifications: Int = 0,
    val library: List<RealmMyLibrary> = emptyList(),
    val courses: List<RealmMyCourse> = emptyList(),
    val teams: List<RealmMyTeam> = emptyList(),
    val navigationEvent: NavigationEvent? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val databaseService: DatabaseService,
    private val userRepository: UserRepository,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository,
    private val teamRepository: TeamRepository,
    private val submissionRepository: SubmissionRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private val user = userRepository.getUserModel()
    private val notificationManager = NotificationUtils.getInstance(context)

    init {
        checkUserSession()
    }

    private fun checkUserSession() {
        if (user == null) {
            _uiState.update { it.copy(userState = UserState.SessionExpired) }
            return
        }

        if (user.id?.startsWith("guest") == true && userRepository.getOfflineVisits() >= 3) {
            _uiState.update { it.copy(userState = UserState.GuestTrialExpired) }
            return
        }

        if (user.rolesList?.isEmpty() == true && !user.userAdmin!!) {
            _uiState.update { it.copy(userState = UserState.Inactive) }
            return
        }
    }

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

    fun processNotifications(
        onNotificationCreated: (NotificationUtils.NotificationConfig) -> Unit
    ) {
        viewModelScope.launch {
            val userId = user?.id
            var unreadCount = 0
            val newNotifications = mutableListOf<NotificationUtils.NotificationConfig>()

            try {
                updateResourceNotification(userId)

                val taskData = teamRepository.getTaskNotifications(userId)
                val joinRequestData = teamRepository.getJoinRequestNotifications(userId)

                databaseService.realmInstance.use { backgroundRealm ->
                    val createdNotifications = createNotifications(backgroundRealm, userId, taskData, joinRequestData)
                    newNotifications.addAll(createdNotifications)
                    unreadCount = getUnreadNotificationsSize(userId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                try {
                    setUnreadNotifications(unreadCount)

                    val groupedNotifications = newNotifications.groupBy { it.type }

                    groupedNotifications.forEach { (type, notifications) ->
                        when {
                            notifications.size == 1 -> onNotificationCreated(notifications.first())
                            notifications.size > 1 -> {
                                val summaryConfig = createSummaryNotification(type, notifications.size)
                                onNotificationCreated(summaryConfig)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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

    private suspend fun createNotifications(
        realm: Realm,
        userId: String?,
        taskData: List<Triple<String, String, String>>,
        joinRequestData: List<JoinRequestNotification>
    ): List<NotificationUtils.NotificationConfig> {
        val surveyTitles = collectSurveyData(realm, userId)
        val storageRatio = FileUtils.totalAvailableMemoryRatio(context)

        val notificationConfigs = realm.where(RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("isRead", false)
            .findAll()
            .mapNotNull { dbNotification ->
                createNotificationConfigFromDatabase(dbNotification)
            }
            .toMutableList()

        surveyTitles.forEach { title ->
            createNotificationIfMissing("survey", title, title, userId)
        }

        taskData.forEach { (title, deadline, id) ->
            createNotificationIfMissing("task", "$title $deadline", id, userId)
        }

        if (storageRatio > 85) {
            createNotificationIfMissing("storage", "$storageRatio%", "storage", userId)
        }

        joinRequestData.forEach { (requesterName, teamName, requestId) ->
            val message = context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
            createNotificationIfMissing("join_request", message, requestId, userId)
        }
        return notificationConfigs
    }

    private fun collectSurveyData(realm: Realm, userId: String?): List<String> {
        return realm.where(RealmSubmission::class.java)
            .equalTo("userId", userId)
            .equalTo("status", "pending")
            .equalTo("type", "survey")
            .findAll()
            .mapNotNull { submission ->
                val examId = submission.parentId?.split("@")?.firstOrNull() ?: ""
                realm.where(RealmStepExam::class.java)
                    .equalTo("id", examId)
                    .findFirst()
                    ?.name
            }
    }

    private fun createNotificationConfigFromDatabase(dbNotification: RealmNotification): NotificationUtils.NotificationConfig? {
        return when (dbNotification.type.lowercase()) {
            "survey" -> notificationManager.createSurveyNotification(
                dbNotification.id,
                dbNotification.message
            ).copy(
                extras = mapOf("surveyId" to (dbNotification.relatedId ?: dbNotification.id))
            )
            "task" -> {
                val parts = dbNotification.message.split(" ")
                val taskTitle = parts.dropLast(3).joinToString(" ")
                val deadline = parts.takeLast(3).joinToString(" ")
                notificationManager.createTaskNotification(dbNotification.id, taskTitle, deadline).copy(
                    extras = mapOf("taskId" to (dbNotification.relatedId ?: dbNotification.id))
                )
            }
            "resource" -> notificationManager.createResourceNotification(
                dbNotification.id,
                dbNotification.message.toIntOrNull() ?: 0
            )
            "storage" -> {
                val storageValue = dbNotification.message.replace("%", "").toIntOrNull() ?: 0
                notificationManager.createStorageWarningNotification(storageValue, dbNotification.id)
            }
            "join_request" -> notificationManager.createJoinRequestNotification(
                dbNotification.id,
                "New Request",
                dbNotification.message
            ).copy(
                extras = mapOf("requestId" to (dbNotification.relatedId ?: dbNotification.id), "teamName" to dbNotification.message)
            )
            else -> null
        }
    }

    private fun createSummaryNotification(type: String, count: Int): NotificationUtils.NotificationConfig {
        val summaryId = "summary_${type}"

        return when (type) {
            "survey" -> NotificationUtils.NotificationConfig(
                id = summaryId,
                type = type,
                title = "📋 New Surveys Available",
                message = "$count new surveys are waiting for you",
                priority = NotificationCompat.PRIORITY_HIGH,
                category = NotificationCompat.CATEGORY_REMINDER
            )
            "task" -> NotificationUtils.NotificationConfig(
                id = summaryId,
                type = type,
                title = "✅ New Tasks Assigned",
                message = "$count new tasks have been assigned to you",
                priority = NotificationCompat.PRIORITY_HIGH,
                category = NotificationCompat.CATEGORY_REMINDER
            )
            "join_request" -> NotificationUtils.NotificationConfig(
                id = summaryId,
                type = type,
                title = "👥 Team Join Requests",
                message = "$count new team join requests to review",
                priority = NotificationCompat.PRIORITY_DEFAULT,
                category = NotificationCompat.CATEGORY_SOCIAL
            )
            "resource" -> NotificationUtils.NotificationConfig(
                id = summaryId,
                type = type,
                title = "📚 New Resources Available",
                message = "$count new resources have been added",
                priority = NotificationCompat.PRIORITY_DEFAULT,
                category = NotificationCompat.CATEGORY_RECOMMENDATION
            )
            "storage" -> NotificationUtils.NotificationConfig(
                id = summaryId,
                type = type,
                title = "⚠️ Storage Warnings",
                message = "$count storage warnings need attention",
                priority = NotificationCompat.PRIORITY_DEFAULT,
                category = NotificationCompat.CATEGORY_STATUS
            )
            else -> NotificationUtils.NotificationConfig(
                id = summaryId,
                type = type,
                title = "📱 App Notifications",
                message = "$count new notifications",
                priority = NotificationCompat.PRIORITY_DEFAULT,
                category = NotificationCompat.CATEGORY_MESSAGE
            )
        }
    }

    fun handleIntent(intent: Intent?) {
        val fromNotification = intent?.getBooleanExtra("from_notification", false) ?: false
        if (fromNotification) {
            val notificationType = intent.getStringExtra("notification_type")
            val notificationId = intent.getStringExtra("notification_id")
            notificationId?.let { markNotificationAsRead(it) }

            when (notificationType) {
                NotificationUtils.TYPE_SURVEY -> {
                    val surveyId = intent.getStringExtra("surveyId")
                    _uiState.update {
                        it.copy(navigationEvent = surveyId?.let { it1 -> NavigationEvent.ToSurvey(it1) })
                    }
                }
                NotificationUtils.TYPE_TASK -> {
                    val taskId = intent.getStringExtra("taskId")
                    viewModelScope.launch {
                        handleTaskNavigation(taskId)
                    }
                }
                NotificationUtils.TYPE_STORAGE -> _uiState.update { it.copy(navigationEvent = NavigationEvent.ToSettings) }
                NotificationUtils.TYPE_JOIN_REQUEST -> {
                    val teamName = intent.getStringExtra("teamName")
                    viewModelScope.launch {
                        handleJoinRequestNavigation(teamName)
                    }
                }
                else -> _uiState.update { it.copy(navigationEvent = NavigationEvent.ToNotifications) }
            }
        }
        if (intent?.getBooleanExtra("auto_navigate", false) == true) {
            val notificationType = intent.getStringExtra("notification_type")
            val relatedId = intent.getStringExtra("related_id")
            when (notificationType) {
                NotificationUtils.TYPE_SURVEY -> {
                    viewModelScope.launch {
                        handleSurveyNavigation(relatedId)
                    }
                }
                NotificationUtils.TYPE_TASK -> {
                    viewModelScope.launch {
                        handleTaskNavigation(relatedId)
                    }
                }
                NotificationUtils.TYPE_JOIN_REQUEST -> {
                    viewModelScope.launch {
                        handleJoinRequestNavigation(relatedId)
                    }
                }
                NotificationUtils.TYPE_RESOURCE -> _uiState.update { it.copy(navigationEvent = NavigationEvent.ToResources) }
            }
        }
    }

    private fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId, user?.id)
        }
    }

    private suspend fun handleSurveyNavigation(surveyId: String?) {
        if (surveyId != null) {
            val currentStepExam = databaseService.withRealmAsync { realm ->
                realm.where(RealmStepExam::class.java).equalTo("name", surveyId)
                    .findFirst()?.let {
                        realm.copyFromRealm(it)
                    }
            }
            _uiState.update {
                it.copy(
                    navigationEvent = NavigationEvent.OpenSurvey(
                        currentStepExam?.id,
                        false,
                        false,
                        ""
                    )
                )
            }
        }
    }

    private suspend fun handleTaskNavigation(taskId: String?) {
        if (taskId == null) return
        val teamData = teamRepository.getTaskTeamInfo(taskId)
        teamData?.let { (teamId, teamName, teamType) ->
            _uiState.update {
                it.copy(
                    navigationEvent = NavigationEvent.ToTask(
                        teamId,
                        teamName,
                        teamType
                    )
                )
            }
        }
    }

    private suspend fun handleJoinRequestNavigation(requestId: String?) {
        if (requestId != null) {
            val actualJoinRequestId = if (requestId.startsWith("join_request_")) {
                requestId.removePrefix("join_request_")
            } else {
                requestId
            }

            val teamId = teamRepository.getJoinRequestTeamId(actualJoinRequestId)

            if (teamId?.isNotEmpty() == true) {
                _uiState.update { it.copy(navigationEvent = NavigationEvent.ToJoinRequest(teamId)) }
            }
        }
    }

    fun consumeNavigationEvent() {
        _uiState.update { it.copy(navigationEvent = null) }
    }
}
