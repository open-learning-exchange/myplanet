package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.NetworkUtils.isNetworkConnectedFlow
import org.ole.planet.myplanet.utils.TimeProvider

@HiltViewModel
class BellDashboardViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val teamsRepository: TeamsRepository,
    private val surveysRepository: SurveysRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val userRepository: UserRepository,
    private val coursesRepository: CoursesRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    companion object {
        private val SURVEY_DIALOG_INTERVAL_MS = TimeUnit.HOURS.toMillis(1)
    }
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Disconnected)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _completedCourses = MutableStateFlow<List<CourseCompletion>>(emptyList())
    val completedCourses: StateFlow<List<CourseCompletion>> = _completedCourses.asStateFlow()


    private val _surveyPrompt = kotlinx.coroutines.flow.MutableSharedFlow<SurveyPrompt>(replay = 0)
    val surveyPrompt: kotlinx.coroutines.flow.SharedFlow<SurveyPrompt> = _surveyPrompt.asSharedFlow()

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
        viewModelScope.launch {
            surveysRepository.dueRemindersFlow().collect { ids ->
                handleDueReminders(ids)
            }
        }
    }

    private suspend fun handleDueReminders(remindersToShow: List<String>) {
        val allSurveyIds = LinkedHashSet<String>()
        for (reminder in remindersToShow) {
            for (id in reminder.split(",")) {
                if (id.isNotBlank()) {
                    allSurveyIds.add(id)
                }
            }
        }
        if (allSurveyIds.isEmpty()) return

        val allSubmissions = submissionsRepository.getSubmissionsByIds(allSurveyIds.toList())
        val submissionsById = allSubmissions.associateBy { it.id }

        for (surveyIds in remindersToShow) {
            val surveyIdList = surveyIds.split(",").filter { it.isNotBlank() }
            if (surveyIdList.isEmpty()) continue

            val pendingSurveys = surveyIdList.mapNotNull { id ->
                submissionsById[id]?.takeIf { it.status == "pending" }
            }

            if (pendingSurveys.isNotEmpty()) {
                val surveyTitles = submissionsRepository.getSurveyTitlesFromSubmissions(pendingSurveys)
                _surveyPrompt.emit(SurveyPrompt(pendingSurveys, surveyTitles, isReminder = true))
            }
        }
    }

    fun checkPendingSurveys(userId: String?) {
        viewModelScope.launch {
            val lastShown = surveysRepository.getLastSurveyDialogShown()
            if (timeProvider.now() - lastShown < SURVEY_DIALOG_INTERVAL_MS) return@launch

            val pendingSurveys = submissionsRepository.getUniquePendingSurveys(userId)
            if (pendingSurveys.isNotEmpty()) {
                val surveyIds = pendingSurveys.joinToString(",") { it.id.toString() }
                if (surveysRepository.isReminderScheduled(surveyIds)) return@launch
                val surveyTitles = submissionsRepository.getSurveyTitlesFromSubmissions(pendingSurveys)
                _surveyPrompt.emit(SurveyPrompt(pendingSurveys, surveyTitles, isReminder = false))
            }
        }
    }


    suspend fun markSurveyDialogShown() {
        surveysRepository.setLastSurveyDialogShown(timeProvider.now())
    }

    suspend fun scheduleSurveyReminder(surveyIds: String, timeUnit: TimeUnit, value: Int) {
        surveysRepository.scheduleSurveyReminder(surveyIds, timeUnit, value)
    }

    suspend fun getUserModel() = userRepository.getUserModel()

    suspend fun isCourseCertified(courseId: String) = coursesRepository.isCourseCertified(courseId)


    fun loadCompletedCourses(userId: String) {
        viewModelScope.launch {
            _completedCourses.value = progressRepository.getCompletedCourses(userId)
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

data class SurveyPrompt(
    val pendingSurveys: List<Submission>,
    val surveyTitles: List<String>,
    val isReminder: Boolean
)
