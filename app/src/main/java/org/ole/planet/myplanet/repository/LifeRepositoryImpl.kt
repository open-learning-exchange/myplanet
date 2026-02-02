package org.ole.planet.myplanet.repository

import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife

class LifeRepositoryImpl @Inject constructor(databaseService: DatabaseService) : RealmRepository(databaseService), LifeRepository {
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

    override suspend fun getMyLifeByUserId(userId: String?): List<RealmMyLife> {
        return queryList(RealmMyLife::class.java) {
            equalTo("userId", userId)
        }.sortedBy { it.weight }
    }

    override suspend fun getVisibleMyLifeByUserId(userId: String?): List<RealmMyLife> {
        return queryList(RealmMyLife::class.java) {
            equalTo("userId", userId)
            equalTo("isVisible", true)
        }.sortedBy { it.weight }
    }

    override suspend fun seedMyLifeIfNeeded(userId: String?, defaultList: List<RealmMyLife>) {
        executeTransaction { realm ->
            val count = realm.where(RealmMyLife::class.java).equalTo("userId", userId).count()
            if (count == 0L) {
                var weight = 1
                defaultList.forEach { item ->
                    val ml = realm.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                    ml.title = item.title
                    ml.imageId = item.imageId
                    ml.weight = weight++
                    ml.userId = userId
                    ml.isVisible = true
                }
            }
        }
    }
}
