package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyPersonal

class MyPersonalRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), MyPersonalRepository {

    override suspend fun getAllMyPersonals(): List<RealmMyPersonal> {
        return queryList(RealmMyPersonal::class.java)
    }

    override suspend fun getMyPersonalById(id: String): RealmMyPersonal? {
        return findByField(RealmMyPersonal::class.java, "id", id)
    }

    override suspend fun getMyPersonalsByUserId(userId: String): List<RealmMyPersonal> {
        return queryList(RealmMyPersonal::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun saveMyPersonal(personal: RealmMyPersonal) {
        save(personal)
    }

    override suspend fun updateMyPersonal(id: String, updater: (RealmMyPersonal) -> Unit) {
        update(RealmMyPersonal::class.java, "id", id, updater)
    }

    override suspend fun deleteMyPersonal(id: String) {
        delete(RealmMyPersonal::class.java, "id", id)
    }
}
