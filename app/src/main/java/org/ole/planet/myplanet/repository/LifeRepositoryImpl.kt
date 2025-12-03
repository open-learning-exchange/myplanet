package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife

class LifeRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), LifeRepository {
    override suspend fun getMyLife(userId: String?): List<RealmMyLife> {
        return withRealm { realm ->
            val results = realm.where(RealmMyLife::class.java)
                .equalTo("userId", userId)
                .findAll()
                .sort("weight")
            realm.copyFromRealm(results)
        }
    }

    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        databaseService.withRealmAsync { realm ->
            val myLife = realm.where(RealmMyLife::class.java).equalTo("_id", myLifeId).findFirst()
            myLife?.let {
                it.isVisible = isVisible
            }
        }
    }
}
