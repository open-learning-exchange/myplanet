package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository,
    private val submissionRepository: SubmissionRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    private val _surveyWarning = MutableStateFlow(false)
    val surveyWarning: StateFlow<Boolean> = _surveyWarning.asStateFlow()

    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications: StateFlow<Int> = _unreadNotifications.asStateFlow()

    // Cache for resource notification updates to prevent frequent heavy operations
    private var lastResourceNotificationUpdate = 0L
    private val resourceNotificationUpdateThrottleMs = 60000L // 1 minute
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

    suspend fun updateResourceNotification(userId: String?, forceUpdate: Boolean = false) {
        val currentTime = System.currentTimeMillis()

        // OPTIMIZATION: Only update if enough time has passed or force update is requested
        if (!forceUpdate && currentTime - lastResourceNotificationUpdate < resourceNotificationUpdateThrottleMs) {
            android.util.Log.d("DashboardViewModel", "Skipping resource notification update, last update was ${currentTime - lastResourceNotificationUpdate}ms ago")
            return
        }

        android.util.Log.d("DashboardViewModel", "Updating resource notifications")
        val startTime = System.currentTimeMillis()
        val resourceCount = libraryRepository.countLibrariesNeedingUpdate(userId)
        val endTime = System.currentTimeMillis()
        android.util.Log.d("DashboardViewModel", "countLibrariesNeedingUpdate took ${endTime - startTime}ms, found $resourceCount resources")

        notificationRepository.updateResourceNotification(userId, resourceCount)
        lastResourceNotificationUpdate = currentTime
    }

    suspend fun createNotificationIfMissing(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    ) {
        notificationRepository.createNotificationIfMissing(type, message, relatedId, userId)
    }

    suspend fun createNotificationsIfMissing(
        notifications: List<NotificationRepository.NotificationData>,
        userId: String?,
    ) {
        notificationRepository.createNotificationsIfMissing(notifications, userId)
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
}
