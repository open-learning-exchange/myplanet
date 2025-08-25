package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Realm
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.ResourceRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val databaseService: DatabaseService,
    private val userRepository: UserRepository,
    private val resourceRepository: ResourceRepository,
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

    fun loadDashboardData(userId: String?) {
        loadSurveyWarning(userId)
        loadUnreadNotifications(userId)
    }

    private fun loadSurveyWarning(userId: String?) {
        viewModelScope.launch {
            val count = submissionRepository.getSubmissionCountByUser(userId)
            _surveyWarning.value = count == 0
        }
    }

    private fun loadUnreadNotifications(userId: String?) {
        viewModelScope.launch {
            _unreadNotifications.value = notificationRepository.getUnreadCount(userId)
        }
    }

    suspend fun updateResourceNotification(userId: String?) {
        try {
            databaseService.executeTransactionAsync { realm ->
                val resourceCount = BaseResourceFragment.getLibraryList(realm, userId).size
                if (resourceCount > 0) {
                    val existingNotification = realm.where(RealmNotification::class.java)
                        .equalTo("userId", userId)
                        .equalTo("type", "resource")
                        .findFirst()

                    if (existingNotification != null) {
                        existingNotification.message = "$resourceCount"
                        existingNotification.relatedId = "$resourceCount"
                    } else {
                        createNotificationIfNotExists(realm, "resource", "$resourceCount", "$resourceCount", userId)
                    }
                } else {
                    realm.where(RealmNotification::class.java)
                        .equalTo("userId", userId)
                        .equalTo("type", "resource")
                        .findFirst()?.deleteFromRealm()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createNotificationIfNotExists(realm: Realm, type: String, message: String, relatedId: String?, userId: String?) {
        val existingNotification = realm.where(RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("type", type)
            .equalTo("relatedId", relatedId)
            .findFirst()

        if (existingNotification == null) {
            realm.createObject(RealmNotification::class.java, "${UUID.randomUUID()}").apply {
                this.userId = userId ?: ""
                this.type = type
                this.message = message
                this.relatedId = relatedId
                this.createdAt = Date()
            }
        }
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
