package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.data.DatabaseService
import javax.inject.Inject

class DatabaseRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : DatabaseRepository {
    override suspend fun clearAll() {
        databaseService.withRealmAsync { realm ->
            realm.executeTransaction { transactionRealm ->
                transactionRealm.deleteAll()
            }
        }
    }
}
