package org.ole.planet.myplanet.repository

import io.realm.Realm
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

class NotificationRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
) : NotificationRepository {

    override suspend fun updateResourceNotification(userId: String?) {
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
                        createNotificationIfNotExists(
                            realm,
                            "resource",
                            "$resourceCount",
                            "$resourceCount",
                            userId,
                        )
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

    private fun createNotificationIfNotExists(
        realm: Realm,
        type: String,
        message: String,
        relatedId: String?,
        userId: String?,
    ) {
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

    override suspend fun getSurveyTitlesFromSubmissions(
        submissions: List<RealmSubmission>,
    ): List<String> {
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

    override suspend fun getUnreadNotificationsSize(userId: String?): Int {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .count()
                .toInt()
        }
    }
}
