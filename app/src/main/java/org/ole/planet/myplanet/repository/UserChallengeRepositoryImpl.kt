package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmUserChallengeActions
import javax.inject.Inject

class UserChallengeRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : RealmRepository(databaseService), UserChallengeRepository {
    override suspend fun hasValidSync(userId: String?): Boolean {
        return withRealm { realm ->
            realm.where(RealmUserChallengeActions::class.java)
                .equalTo("userId", userId)
                .equalTo("actionType", "sync")
                .count() > 0
        }
    }
}
