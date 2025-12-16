package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.service.UserProfileDbHandler.Companion.KEY_LOGIN
import javax.inject.Inject

class OfflineActivityRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), OfflineActivityRepository {
    override suspend fun getOfflineLogins(userName: String): Flow<List<RealmOfflineActivity>> {
        return queryListFlow(RealmOfflineActivity::class.java) {
            equalTo("userName", userName).equalTo("type", KEY_LOGIN)
        }
    }
}
