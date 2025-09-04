package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNotification

class NotificationRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), NotificationRepository {

    override suspend fun getUnreadCount(userId: String?): Int {
        return count(RealmNotification::class.java) {
            equalTo("userId", userId)
            equalTo("isRead", false)
        }.toInt()
    }
}
