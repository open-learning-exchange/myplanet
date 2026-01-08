package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife

class LifeRepositoryImpl @Inject constructor(databaseService: DatabaseService) : RealmRepository(databaseService), LifeRepository {
    override suspend fun getMyLifeByUserId(userId: String): List<RealmMyLife> {
        return withRealm { realm ->
            val results = realm.where(RealmMyLife::class.java)
                .equalTo("userId", userId)
                .sort("weight")
                .findAll()
            realm.copyFromRealm(results)
        }
    }

    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        executeTransaction { realm ->
            val myLife = realm.where(RealmMyLife::class.java).equalTo("_id", myLifeId).findFirst()
            myLife?.let {
                it.isVisible = isVisible
            }
        }
    }

    override suspend fun updateMyLifeListOrder(list: List<RealmMyLife>) {
        executeTransaction { realm ->
            list.forEachIndexed { index, myLife ->
                val realmMyLife = realm.where(RealmMyLife::class.java).equalTo("_id", myLife._id).findFirst()
                realmMyLife?.weight = index
            }
        }
    }
}
