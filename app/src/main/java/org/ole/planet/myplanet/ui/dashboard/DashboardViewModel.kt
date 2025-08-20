package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Realm
import javax.inject.Inject
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository,
    private val submissionRepository: SubmissionRepository
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
        notificationRepository.updateResourceNotification(userId)
    }

    fun createNotificationIfNotExists(
        realm: Realm,
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    ) {
        notificationRepository.createNotificationIfNotExists(realm, type, message, relatedId, userId)
    }

    suspend fun getPendingSurveysAsync(userId: String?): List<RealmSubmission> {
        return submissionRepository.getPendingSurveysAsync(userId)
    }

    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> {
        return notificationRepository.getSurveyTitlesFromSubmissions(submissions)
    }

    suspend fun getUnreadNotificationsSize(userId: String?): Int {
        return notificationRepository.getUnreadNotificationsSize(userId)
    }
}
