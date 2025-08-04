package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Realm
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class DashboardViewModel @Inject constructor(
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

    fun updateResourceNotification(userId: String?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
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
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    suspend fun getPendingSurveysAsync(userId: String?): List<RealmSubmission> {
        return submissionRepository.getPendingSurveysAsync(userId)
    }

    fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        return submissionRepository.getPendingSurveys(userId)
    }

    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> {
        return databaseService.withRealmAsync { realm ->
            val titles = mutableListOf<String>()
            submissions.forEach { submission ->
                val examId = submission.parentId?.split("@")?.firstOrNull() ?: ""
                val exam = realm.where(RealmStepExam::class.java)
                    .equalTo("id", examId)
                    .findFirst()
                exam?.name?.let { titles.add(it) }
            }
            titles
        }
    }

    suspend fun getUnreadNotificationsSize(userId: String?): Int {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .count()
                .toInt()
        }
    }

    @Deprecated("Use async version without realm parameter", ReplaceWith("getUnreadNotificationsSize(userId)"))
    fun getUnreadNotificationsSize(realm: Realm, userId: String?): Int {
        return realm.where(RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("isRead", false)
            .count()
            .toInt()
    }
    
    @Deprecated("Use async version", ReplaceWith("getSurveyTitlesFromSubmissions(submissions)"))
    fun getSurveyTitlesFromSubmissions(realm: Realm, submissions: List<RealmSubmission>): List<String> {
        val titles = mutableListOf<String>()
        submissions.forEach { submission ->
            val examId = submission.parentId?.split("@")?.firstOrNull() ?: ""
            val exam = realm.where(RealmStepExam::class.java)
                .equalTo("id", examId)
                .findFirst()
            exam?.name?.let { titles.add(it) }
        }
        return titles
    }
    
    @Deprecated("Use async version", ReplaceWith("updateResourceNotification(userId)"))
    fun updateResourceNotification(realm: Realm, userId: String?) {
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
}
