package org.ole.planet.myplanet.presentation.dashboard

import androidx.lifecycle.ViewModel
import org.ole.planet.myplanet.domain.repository.NotificationRepository

class DashboardViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

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

    fun updateResourceNotification(userId: String?) =
        notificationRepository.updateResourceNotification(userId)

    fun createNotificationIfNotExists(type: String, message: String, relatedId: String?, userId: String?) =
        notificationRepository.createNotificationIfNotExists(type, message, relatedId, userId)

    fun getPendingSurveys(userId: String?) = notificationRepository.getPendingSurveys(userId)

    fun getSurveyTitlesFromSubmissions(submissions: List<org.ole.planet.myplanet.model.RealmSubmission>): List<String> =
        notificationRepository.getSurveyTitlesFromSubmissions(submissions)

    fun getUnreadNotificationsSize(userId: String?) = notificationRepository.getUnreadNotificationsSize(userId)
}
