package org.ole.planet.myplanet.repository

import io.realm.Realm
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNotification

class NotificationRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), NotificationRepository {

    override suspend fun getUnreadCount(userId: String?): Int {
        return withRealm { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .count()
                .toInt()
        }
    }

    override suspend fun updateResourceNotification(userId: String?) {
        executeTransaction { realm ->
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
    }

    override fun createNotificationIfNotExists(
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
}
