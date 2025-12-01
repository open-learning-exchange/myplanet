package org.ole.planet.myplanet.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayList
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
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
import org.ole.planet.myplanet.service.DatabaseService

data class DashboardUiState(
    val unreadNotifications: Int = 0,
    val library: List<RealmMyLibrary> = emptyList(),
    val courses: List<RealmMyCourse> = emptyList(),
    val teams: List<RealmMyTeam> = emptyList(),
    val myLife: List<MyLifeItem> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    private val settings: SharedPreferences,
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
                    _uiState.update { it.copy(teams = teams) }
                }
            }

            launch {
                loadMyLife(userId)
            }
        }
    }

    private suspend fun loadMyLife(userId: String?) {
        setUpMyLife(userId)
        val myLifeItems = databaseService.withRealmAsync { realm ->
            RealmMyLife.getMyLifeByUserId(realm, settings)
                .filter { it.isVisible }
                .map {
                    MyLifeItem(
                        title = it.title ?: "",
                        imageId = it.imageId,
                        isVisible = it.isVisible
                    )
                }
        }
        _uiState.update { it.copy(myLife = myLifeItems) }
    }

    private fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeListBase: MutableList<RealmMyLife> = ArrayList()
        myLifeListBase.add(RealmMyLife(R.drawable.mylife_achievements, context.getString(R.string.my_achievements), userId))
        myLifeListBase.add(RealmMyLife(R.drawable.mylife_calendar, context.getString(R.string.my_calendar), userId))
        myLifeListBase.add(RealmMyLife(R.drawable.mylife_goals, context.getString(R.string.my_goals), userId))
        myLifeListBase.add(RealmMyLife(R.drawable.mylife_help, context.getString(R.string.my_help), userId))
        myLifeListBase.add(RealmMyLife(R.drawable.mylife_personel, context.getString(R.string.my_personals), userId))
        myLifeListBase.add(RealmMyLife(R.drawable.mylife_records, context.getString(R.string.my_records), userId))
        myLifeListBase.add(RealmMyLife(R.drawable.mylife_submissions, context.getString(R.string.my_submissions), userId))
        return myLifeListBase
    }

    private suspend fun setUpMyLife(userId: String?) {
        val myLifeExists = databaseService.withRealmAsync { realm ->
            RealmMyLife.getMyLifeByUserId(realm, settings).isNotEmpty()
        }

        if (!myLifeExists) {
            val myLifeListBase = getMyLifeListBase(userId)
            databaseService.executeTransactionAsync { realm ->
                var weight = 1
                for (item in myLifeListBase) {
                    val ml = realm.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                    ml.title = item.title
                    ml.imageId = item.imageId
                    ml.weight = weight
                    ml.userId = item.userId
                    ml.isVisible = true
                    weight++
                }
            }
        }
    }
}
