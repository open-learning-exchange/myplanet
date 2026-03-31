package org.ole.planet.myplanet.repository

import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmMyLife

class LifeRepositoryImpl @Inject constructor(databaseService: DatabaseService, @RealmDispatcher realmDispatcher: CoroutineDispatcher) : RealmRepository(databaseService, realmDispatcher), LifeRepository {
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
            val ids = list.mapNotNull { it._id }.toTypedArray()
            if (ids.isEmpty()) return@executeTransaction
            val idToIndexMap = list.mapIndexedNotNull { index, life ->
                life._id?.let { it to index }
            }.toMap()

            val managedLives = realm.where(RealmMyLife::class.java).`in`("_id", ids).findAll()
            managedLives.forEach { managedLife ->
                val index = idToIndexMap[managedLife._id]
                if (index != null) {
                    managedLife.weight = index
                }
            }
        }
    }

    override suspend fun getMyLifeByUserId(userId: String?): List<RealmMyLife> {
        return queryList(RealmMyLife::class.java, true) {
            equalTo("userId", userId)
        }.sortedBy { it.weight }
    }

    override suspend fun seedMyLifeIfEmpty(userId: String?, items: List<RealmMyLife>) {
        executeTransaction { realm ->
            val existing = realm.where(RealmMyLife::class.java).equalTo("userId", userId).findAll()
            if (existing.isEmpty()) {
                var weight = 1
                val newItems = items.map { item ->
                    RealmMyLife().apply {
                        _id = UUID.randomUUID().toString()
                        title = item.title
                        imageId = item.imageId
                        this.weight = weight++
                        this.userId = item.userId
                        isVisible = true
                    }
                }
                realm.insertOrUpdate(newItems)
            }
        }
    }
}
