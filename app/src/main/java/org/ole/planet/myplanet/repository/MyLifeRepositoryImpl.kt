package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife
import kotlinx.coroutines.flow.Flow

class MyLifeRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), MyLifeRepository {
    override fun getMyLife(): Flow<List<RealmMyLife>> {
        return queryListFlow(RealmMyLife::class.java) {
            equalTo("isVisible", true)
        }
    }
}
