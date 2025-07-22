package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.di.DashboardRepository

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DashboardRepository
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

    suspend fun updateResourceNotification(userId: String?) {
        repository.updateResourceNotification(userId)
    }

    suspend fun getPendingSurveyTitles(userId: String?): List<String> {
        return repository.getPendingSurveyTitles(userId)
    }

    suspend fun getUnreadNotificationsSize(userId: String?): Int {
        return repository.getUnreadNotificationsSize(userId)
    }

    suspend fun createNotificationIfNotExists(type: String, message: String, relatedId: String?, userId: String?) {
        repository.createNotificationIfNotExists(type, message, relatedId, userId)
    }

    suspend fun getMyLibraryByUser() = repository.getMyLibraryByUser()
}

