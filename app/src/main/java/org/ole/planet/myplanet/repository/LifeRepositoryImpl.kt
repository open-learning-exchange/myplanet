package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.utilities.MyLifeUtil
import java.util.UUID

class LifeRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : LifeRepository, RealmRepository(databaseService) {
    override suspend fun getMyLifeByUserId(userId: String): List<RealmMyLife> {
        return databaseService.withRealm { realm ->
            realm.where(RealmMyLife::class.java)
                .equalTo("userId", userId)
                .findAll()
                .let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun setUpMyLife(userId: String) {
        databaseService.withRealm { realm ->
            if (realm.where(RealmMyLife::class.java).equalTo("userId", userId).count() == 0L) {
                realm.executeTransaction {
                    val myLifeListBase = MyLifeUtil.getMyLifeListBase(userId)
                    var weight = 1
                    for (item in myLifeListBase) {
                        val ml = it.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
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
}
