package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Case
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.CourseRepository
import org.ole.planet.myplanet.di.LibraryRepository
import org.ole.planet.myplanet.di.UserRepository
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val databaseService: DatabaseService,
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

    fun getPendingSurveys(realm: Realm, userId: String?): List<RealmSubmission> {
        return realm.where(RealmSubmission::class.java)
            .equalTo("userId", userId)
            .equalTo("type", "survey")
            .equalTo("status", "pending", Case.INSENSITIVE)
            .findAll()
    }

    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> {
        val examIds = submissions.map { it.parentId?.split("@")?.firstOrNull() ?: "" }
        return withContext(Dispatchers.IO) {
            databaseService.withRealm { realm ->
                examIds.mapNotNull { examId ->
                    realm.where(RealmStepExam::class.java)
                        .equalTo("id", examId)
                        .findFirst()
                        ?.name
                }
            }
        }
    }

    suspend fun getUnreadNotificationsSize(userId: String?): Int {
        return withContext(Dispatchers.IO) {
            databaseService.withRealm { realm ->
                realm.where(RealmNotification::class.java)
                    .equalTo("userId", userId)
                    .equalTo("isRead", false)
                    .count()
                    .toInt()
            }
        }
    }

    suspend fun updateResourceNotification(userId: String?) {
        withContext(Dispatchers.IO) {
            databaseService.withRealm { realm ->
                val resourceCount = BaseResourceFragment.getLibraryList(realm, userId).size
                realm.executeTransaction { r ->
                    if (resourceCount > 0) {
                        val existingNotification = r.where(RealmNotification::class.java)
                            .equalTo("userId", userId)
                            .equalTo("type", "resource")
                            .findFirst()

                        if (existingNotification != null) {
                            existingNotification.message = "$resourceCount"
                            existingNotification.relatedId = "$resourceCount"
                        } else {
                            createNotificationIfNotExists(r, "resource", "$resourceCount", "$resourceCount", userId)
                        }
                    } else {
                        r.where(RealmNotification::class.java)
                            .equalTo("userId", userId)
                            .equalTo("type", "resource")
                            .findFirst()?.deleteFromRealm()
                    }
                }
            }
        }
    }
}

