package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife

class LifeRepositoryImpl @Inject constructor(private val databaseService: DatabaseService) : LifeRepository {
    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        databaseService.executeTransactionAsync { realm ->
            val myLife = realm.where(RealmMyLife::class.java).equalTo("_id", myLifeId).findFirst()
            myLife?.let {
                it.isVisible = isVisible
            }
        }
    }

    override suspend fun updateMyLifeListOrder(list: List<RealmMyLife>) {
        databaseService.executeTransactionAsync { realm ->
            list.forEachIndexed { index, myLife ->
                val realmMyLife = realm.where(RealmMyLife::class.java).equalTo("_id", myLife._id).findFirst()
                realmMyLife?.weight = index
            }
        }
    }
}
