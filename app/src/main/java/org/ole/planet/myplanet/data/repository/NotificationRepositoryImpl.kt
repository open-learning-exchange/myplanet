package org.ole.planet.myplanet.data.repository

import io.realm.Case
import io.realm.Realm
import java.util.Date
import java.util.UUID
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.domain.repository.NotificationRepository
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

class NotificationRepositoryImpl(private val databaseService: DatabaseService) : NotificationRepository {
    private fun <T> useRealm(block: (Realm) -> T): T {
        val realm = databaseService.realmInstance
        return try {
            block(realm)
        } finally {
            if (!realm.isClosed) realm.close()
        }
    }

    override fun updateResourceNotification(userId: String?) {
        useRealm { realm ->
            val resourceCount = BaseResourceFragment.getLibraryList(realm, userId).size
            if (resourceCount > 0) {
                val existing = realm.where(RealmNotification::class.java)
                    .equalTo("userId", userId)
                    .equalTo("type", "resource")
                    .findFirst()
                if (existing != null) {
                    existing.message = "$resourceCount"
                    existing.relatedId = "$resourceCount"
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

    override fun createNotificationIfNotExists(type: String, message: String, relatedId: String?, userId: String?) {
        useRealm { realm ->
            createNotificationIfNotExists(realm, type, message, relatedId, userId)
        }
    }

    fun createNotificationIfNotExists(realm: Realm, type: String, message: String, relatedId: String?, userId: String?) {
        val existing = realm.where(RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("type", type)
            .equalTo("relatedId", relatedId)
            .findFirst()
        if (existing == null) {
            realm.createObject(RealmNotification::class.java, UUID.randomUUID().toString()).apply {
                this.userId = userId ?: ""
                this.type = type
                this.message = message
                this.relatedId = relatedId
                this.createdAt = Date()
            }
        }
    }

    override fun getPendingSurveys(userId: String?): List<RealmSubmission> =
        useRealm { realm ->
            realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("type", "survey")
                .equalTo("status", "pending", Case.INSENSITIVE)
                .findAll()
        }

    override fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> =
        useRealm { realm ->
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

    override fun getUnreadNotificationsSize(userId: String?): Int =
        useRealm { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .count()
                .toInt()
        }
}
