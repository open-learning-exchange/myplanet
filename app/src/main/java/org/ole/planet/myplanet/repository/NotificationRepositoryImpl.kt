package org.ole.planet.myplanet.repository

import javax.inject.Inject
import java.util.Date
import java.util.UUID
import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
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
        try {
            executeTransaction { realm ->
                val resourceCount = realm.where(RealmMyLibrary::class.java)
                    .equalTo("isPrivate", false)
                    .findAll()
                    .filter { it.needToUpdate() && it.userId?.contains(userId) == true }
                    .size

                val existingNotification = realm.where(RealmNotification::class.java)
                    .equalTo("userId", userId)
                    .equalTo("type", "resource")
                    .findFirst()

                if (resourceCount > 0) {
                    if (existingNotification != null) {
                        existingNotification.message = "$resourceCount"
                        existingNotification.relatedId = "$resourceCount"
                    } else {
                        createNotificationIfNotExists(
                            realm,
                            "resource",
                            "$resourceCount",
                            "$resourceCount",
                            userId
                        )
                    }
                } else {
                    existingNotification?.deleteFromRealm()
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
        userId: String?
    ) {
        val existingNotification = realm.where(RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("type", type)
            .equalTo("relatedId", relatedId)
            .findFirst()

        if (existingNotification == null) {
            realm.createObject(RealmNotification::class.java, UUID.randomUUID().toString()).apply {
                this.userId = userId ?: ""
                this.type = type
                this.message = message
                this.relatedId = relatedId
                this.createdAt = Date()
            }
        }
    }
}
