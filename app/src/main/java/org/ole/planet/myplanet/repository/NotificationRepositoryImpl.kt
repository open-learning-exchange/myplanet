package org.ole.planet.myplanet.repository

import javax.inject.Inject
import java.util.Date
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
        userId ?: return

        val resourceCount = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }.count { it.needToUpdate() && it.userId?.contains(userId) == true }

        val existingNotification = findByField(RealmNotification::class.java, "userId", userId)
            ?.takeIf { it.type == "resource" }

        if (resourceCount > 0) {
            val notification = existingNotification?.apply {
                message = "$resourceCount"
                relatedId = "$resourceCount"
            } ?: RealmNotification().apply {
                this.userId = userId
                this.type = "resource"
                this.message = "$resourceCount"
                this.relatedId = "$resourceCount"
                this.createdAt = Date()
            }
            save(notification)
        } else {
            existingNotification?.let { delete(RealmNotification::class.java, "id", it.id) }
        }
    }
}

