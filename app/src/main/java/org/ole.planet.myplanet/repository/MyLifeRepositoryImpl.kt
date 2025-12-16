package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife
import javax.inject.Inject

class MyLifeRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : MyLifeRepository {
    override suspend fun getMyLifeByUserId(userId: String?): List<RealmMyLife> {
        return databaseService.withRealmAsync { realm ->
            realm.copyFromRealm(RealmMyLife.getMyLifeByUserId(realm, userId))
        }
    }
}
