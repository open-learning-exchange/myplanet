package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife

class LifeRepositoryImpl @Inject constructor(databaseService: DatabaseService) : RealmRepository(databaseService), LifeRepository {
    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        update(RealmMyLife::class.java, "_id", myLifeId) { myLife ->
            myLife.isVisible = isVisible
        }
    }

    override suspend fun updateMyLifeListOrder(list: List<RealmMyLife>) {
        executeTransaction { realm ->
            val ids = list.map { it._id }.toTypedArray()
            val realmMyLifeList = realm.where(RealmMyLife::class.java).`in`("_id", ids).findAll()
            val realmMyLifeMap = realmMyLifeList.associateBy { it._id }

            list.forEachIndexed { index, myLife ->
                realmMyLifeMap[myLife._id]?.weight = index
            }
        }
    }
}
