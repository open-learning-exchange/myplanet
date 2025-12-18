package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Sort
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.UserRepository

import org.ole.planet.myplanet.model.RealmUserModel

data class DashboardUiState(
    val unreadNotifications: Int = 0,
    val library: List<RealmMyLibrary> = emptyList(),
    val courses: List<RealmMyCourse> = emptyList(),
    val teams: List<RealmMyTeam> = emptyList(),
    val users: List<RealmUserModel> = emptyList(),
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
        return notificationRepository.getTeamNotificationInfo(teamId, userId)
    }

    suspend fun getTeamNotifications(teamIds: List<String>, userId: String): Map<String, TeamNotificationInfo> {
        return notificationRepository.getTeamNotifications(teamIds, userId)
    }

    fun loadUserContent(userId: String?) {
        if (userId == null) return
        userContentJob?.cancel()
        userContentJob = viewModelScope.launch {
            val libraryFlowJob = launch {
                libraryRepository.getMyLibraryFlow(userId)
                    .map { library -> library.map { it.id } to library }
                    .distinctUntilChanged { old, new -> old.first == new.first }
                    .collect { (_, library) ->
                        _uiState.update { it.copy(library = library) }
                    }
            }

            val coursesFlowJob = launch {
                courseRepository.getMyCoursesFlow(userId)
                    .map { courses -> courses.map { it.id } to courses }
                    .distinctUntilChanged { old, new -> old.first == new.first }
                    .collect { (_, courses) ->
                        _uiState.update { it.copy(courses = courses) }
                    }
            }

            val teamsFlowJob = launch {
                teamRepository.getMyTeamsFlow(userId)
                    .map { teams -> teams.map { it._id } to teams }
                    .distinctUntilChanged { old, new -> old.first == new.first }
                    .collect { (_, teams) ->
                        _uiState.update { it.copy(teams = teams) }
                    }
            }
        }
    }

    suspend fun getLibraryForSelectedUser(userId: String): List<RealmMyLibrary> {
        return libraryRepository.getLibraryForSelectedUser(userId)
    }

    fun loadUsers() {
        viewModelScope.launch {
            val users = userRepository.getUsersSortedBy("joinDate", Sort.DESCENDING)
            _uiState.update { it.copy(users = users) }
        }
    }
}
