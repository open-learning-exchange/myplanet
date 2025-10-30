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
        android.util.Log.d("NotificationFlow", "DashboardViewModel.updateResourceNotification() - START - userId=$userId")
        val startTime = System.currentTimeMillis()
        val resourceCount = libraryRepository.countLibrariesNeedingUpdate(userId)
        android.util.Log.d("NotificationFlow", "DashboardViewModel.updateResourceNotification() - Found $resourceCount resources needing update")
        notificationRepository.updateResourceNotification(userId, resourceCount)
        android.util.Log.d("NotificationFlow", "DashboardViewModel.updateResourceNotification() - COMPLETED in ${System.currentTimeMillis() - startTime}ms")
    }

    suspend fun createNotificationIfMissing(
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    ) {
        android.util.Log.d("NotificationFlow", "DashboardViewModel.createNotificationIfMissing() - type=$type, relatedId=$relatedId, userId=$userId")
        val startTime = System.currentTimeMillis()
        notificationRepository.createNotificationIfMissing(type, message, relatedId, userId)
        android.util.Log.d("NotificationFlow", "DashboardViewModel.createNotificationIfMissing() - COMPLETED in ${System.currentTimeMillis() - startTime}ms")
    }

    suspend fun createNotificationsBatch(
        notifications: List<org.ole.planet.myplanet.repository.NotificationData>,
        userId: String?
    ) {
        android.util.Log.d("NotificationFlow", "DashboardViewModel.createNotificationsBatch() - Creating ${notifications.size} notifications")
        val startTime = System.currentTimeMillis()
        notificationRepository.createNotificationsBatch(notifications, userId)
        android.util.Log.d("NotificationFlow", "DashboardViewModel.createNotificationsBatch() - COMPLETED in ${System.currentTimeMillis() - startTime}ms")
    }

    suspend fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        android.util.Log.d("NotificationFlow", "DashboardViewModel.getPendingSurveys() - userId=$userId")
        return submissionRepository.getPendingSurveys(userId)
    }

    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> {
        android.util.Log.d("NotificationFlow", "DashboardViewModel.getSurveyTitlesFromSubmissions() - submissions count=${submissions.size}")
        return submissionRepository.getSurveyTitlesFromSubmissions(submissions)
    }

    suspend fun getUnreadNotificationsSize(userId: String?): Int {
        android.util.Log.d("NotificationFlow", "DashboardViewModel.getUnreadNotificationsSize() - START - userId=$userId")
        val startTime = System.currentTimeMillis()
        val count = notificationRepository.getUnreadCount(userId)
        android.util.Log.d("NotificationFlow", "DashboardViewModel.getUnreadNotificationsSize() - Result: count=$count in ${System.currentTimeMillis() - startTime}ms")
        return count
    }

    suspend fun cleanupDuplicateNotifications(userId: String?) {
        android.util.Log.d("NotificationFlow", "DashboardViewModel.cleanupDuplicateNotifications() - Cleaning up duplicates for userId=$userId")
        notificationRepository.cleanupDuplicateNotifications(userId)
    }
}
