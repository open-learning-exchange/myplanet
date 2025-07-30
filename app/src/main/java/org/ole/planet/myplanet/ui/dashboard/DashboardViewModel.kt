package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Case
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.di.CourseRepository
import org.ole.planet.myplanet.di.LibraryRepository
import org.ole.planet.myplanet.di.UserRepository
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository
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

    suspend fun updateResourceNotification(userId: String?) = withContext(Dispatchers.IO) {
        userRepository.getRealm().use { realm ->
            realm.executeTransaction { transactionRealm ->
                val resourceCount = BaseResourceFragment.getLibraryList(transactionRealm, userId).size
                if (resourceCount > 0) {
                    val existingNotification = transactionRealm.where(RealmNotification::class.java)
                        .equalTo("userId", userId)
                        .equalTo("type", "resource")
                        .findFirst()

                    if (existingNotification != null) {
                        existingNotification.message = "$resourceCount"
                        existingNotification.relatedId = "$resourceCount"
                    } else {
                        createNotificationIfNotExists(transactionRealm, "resource", "$resourceCount", "$resourceCount", userId)
                    }
                } else {
                    transactionRealm.where(RealmNotification::class.java)
                        .equalTo("userId", userId)
                        .equalTo("type", "resource")
                        .findFirst()?.deleteFromRealm()
                }
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

    suspend fun getPendingSurveys(userId: String?): List<RealmSubmission> = withContext(Dispatchers.IO) {
        userRepository.getRealm().use { realm ->
            val results = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("type", "survey")
                .equalTo("status", "pending", Case.INSENSITIVE)
                .findAll()
            realm.copyFromRealm(results)
        }
    }

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

    suspend fun getUnreadNotificationsSize(userId: String?): Int = withContext(Dispatchers.IO) {
        userRepository.getRealm().use { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .count()
                .toInt()
        }
    }
}

