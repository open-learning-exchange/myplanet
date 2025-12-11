package org.ole.planet.myplanet.repository

import javax.inject.Inject
import android.content.SharedPreferences
import java.util.UUID
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.DefaultPreferences
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.ui.dashboard.MyLife

import org.ole.planet.myplanet.ui.dashboard.MyLifeProvider

class LifeRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
    @DefaultPreferences private val settings: SharedPreferences,
    private val myLifeProvider: MyLifeProvider
) : LifeRepository {
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
                val realmMyLife =
                    realm.where(RealmMyLife::class.java).equalTo("_id", myLife._id).findFirst()
                realmMyLife?.weight = index
            }
        }
    }

    override suspend fun setUpMyLife(userId: String?) {
        databaseService.executeTransactionAsync { realm ->
            val realmObjects = RealmMyLife.getMyLifeByUserId(realm, settings)
            if (realmObjects.isEmpty()) {
                val myLifeListBase = myLifeProvider.getMyLifeListBase(userId)
                var ml: RealmMyLife
                var weight = 1
                for (item in myLifeListBase) {
                    ml = realm.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                    ml.title = item.title
                    ml.imageId = item.imageId
                    ml.weight = weight
                    ml.userId = item.userId
                    ml.isVisible = true
                    weight++
                }
            }
        }
    }
}
