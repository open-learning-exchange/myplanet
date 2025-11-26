package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife

class LifeRepositoryImpl @Inject constructor(private val databaseService: DatabaseService) : LifeRepository {
    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        databaseService.withRealmAsync { realm ->
            val myLife = realm.where(RealmMyLife::class.java).equalTo("_id", myLifeId).findFirst()
            myLife?.let {
                it.isVisible = isVisible
            }
        }
    }

    override suspend fun updateWeight(weight: Int, id: String?, userId: String?) {
        databaseService.withRealmAsync { realm ->
            val targetItem = realm.where(RealmMyLife::class.java)
                .equalTo("_id", id)
                .findFirst()

            targetItem?.let { item ->
                val currentWeight = item.weight
                item.weight = weight

                val otherItem = realm.where(RealmMyLife::class.java)
                    .equalTo("userId", userId)
                    .equalTo("weight", weight)
                    .notEqualTo("_id", id)
                    .findFirst()

                otherItem?.weight = currentWeight
            }
        }
    }
}
