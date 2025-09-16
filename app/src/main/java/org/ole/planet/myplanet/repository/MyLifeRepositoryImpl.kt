package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife

class MyLifeRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), MyLifeRepository {

    override suspend fun getMyLifeByUserId(userId: String?): List<RealmMyLife> {
        if (userId == null) return emptyList()

        return withRealm { realm ->
            realm.where(RealmMyLife::class.java)
                .equalTo("userId", userId)
                .findAll()
                .sort("weight")
                .let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun updateWeight(weight: Int, id: String?, userId: String?) {
        if (id.isNullOrEmpty() || userId == null) return

        executeTransaction { realm ->
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

    override suspend fun updateVisibility(isVisible: Boolean, id: String?) {
        if (id.isNullOrEmpty()) return

        update(RealmMyLife::class.java, "_id", id) { it.isVisible = isVisible }
    }
}
