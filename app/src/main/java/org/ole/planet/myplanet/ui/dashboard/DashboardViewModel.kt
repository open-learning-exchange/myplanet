package org.ole.planet.myplanet.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import io.realm.kotlin.where
import kotlinx.coroutines.launch
import java.util.UUID
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.UserRepository

data class DashboardUiState(
    val unreadNotifications: Int = 0,
    val library: List<RealmMyLibrary> = emptyList(),
    val courses: List<RealmMyCourse> = emptyList(),
    val teams: List<RealmMyTeam> = emptyList(),
    val myLife: List<RealmMyLife> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository,
    private val teamRepository: TeamRepository,
    private val submissionRepository: SubmissionRepository,
    private val notificationRepository: NotificationRepository,
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

    suspend fun getUnreadNotificationsSize(userId: String?): Int {
        return notificationRepository.getUnreadCount(userId)
    }

    fun loadUserContent(userId: String?) {
        if (userId == null) return
        userContentJob?.cancel()
        userContentJob = viewModelScope.launch(Dispatchers.IO) {
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

            launch {
                databaseService.withRealm { realm ->
                    var myLife = realm.where<RealmMyLife>().equalTo("userId", userId).findAll().let {
                        realm.copyFromRealm(it)
                    }
                    if (myLife.isEmpty()) {
                        realm.executeTransaction {
                            val myLifeListBase = getMyLifeListBase(userId)
                            var weight = 1
                            for (item in myLifeListBase) {
                                val ml = it.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                                ml.title = item.title
                                ml.imageId = item.imageId
                                ml.weight = weight
                                ml.userId = item.userId
                                ml.isVisible = true
                                weight++
                            }
                        }
                        myLife = realm.where<RealmMyLife>().equalTo("userId", userId).findAll().let {
                            realm.copyFromRealm(it)
                        }
                    }
                    _uiState.update { it.copy(myLife = myLife) }
                }
            }
        }
    }

    private fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeList: MutableList<RealmMyLife> = ArrayList()
        myLifeList.add(RealmMyLife("ic_myhealth", userId, context.getString(R.string.myhealth)))
        myLifeList.add(RealmMyLife("my_achievement", userId, context.getString(R.string.achievements)))
        myLifeList.add(RealmMyLife("ic_submissions", userId, context.getString(R.string.submission)))
        myLifeList.add(RealmMyLife("ic_my_survey", userId, context.getString(R.string.my_survey)))
        myLifeList.add(RealmMyLife("ic_references", userId, context.getString(R.string.references)))
        myLifeList.add(RealmMyLife("ic_calendar", userId, context.getString(R.string.calendar)))
        myLifeList.add(RealmMyLife("ic_mypersonals", userId, context.getString(R.string.mypersonals)))
        return myLifeList
    }
}
