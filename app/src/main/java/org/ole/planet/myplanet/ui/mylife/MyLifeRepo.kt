package org.ole.planet.myplanet.ui.mylife

import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.repository.RealmRepository
import javax.inject.Inject

class MyLifeRepo @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService) {
    fun updateVisibility(id: String, isVisible: Boolean) {
        databaseService.withRealm {
            it.executeTransaction {
                val myLife = it.where(RealmMyLife::class.java).equalTo("_id", id).findFirst()
                myLife?.isVisible = isVisible
            }
        }
    }

    fun updateWeight(id: String, weight: Int, userId: String) {
        databaseService.withRealm {
            it.executeTransaction {
                val myLife = it.where(RealmMyLife::class.java).equalTo("_id", id).equalTo("userId", userId).findFirst()
                myLife?.weight = weight
            }
        }
    }
}
