package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.di.DashboardRepository
import org.ole.planet.myplanet.model.RealmSubmission
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository
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

    fun updateResourceNotification(userId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            dashboardRepository.updateResourceNotification(userId)
        }
    }

    fun createNotificationIfNotExists(type: String, message: String, relatedId: String?, userId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            dashboardRepository.createNotificationIfNotExists(type, message, relatedId, userId)
        }
    }

    fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        return dashboardRepository.getPendingSurveys(userId)
    }

    fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> {
        return dashboardRepository.getSurveyTitlesFromSubmissions(submissions)
    }

    fun getUnreadNotificationsSize(userId: String?): Int {
        return dashboardRepository.getUnreadNotificationsSize(userId)
    }
}

