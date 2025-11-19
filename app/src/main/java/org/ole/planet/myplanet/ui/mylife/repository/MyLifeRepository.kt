package org.ole.planet.myplanet.ui.mylife.repository

import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife
import javax.inject.Inject

class MyLifeRepository @Inject constructor(private val databaseService: DatabaseService) {
    suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        databaseService.withRealmAsync { realm ->
            val myLife = realm.where(RealmMyLife::class.java).equalTo("_id", myLifeId).findFirst()
            myLife?.let {
                it.isVisible = isVisible
            }
        }
    }
}