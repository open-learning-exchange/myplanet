package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination

class HealthRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), HealthRepository {

    override suspend fun getHealthExaminationByUserId(userId: String): RealmHealthExamination? {
        return findByField(RealmHealthExamination::class.java, "_id", userId)
            ?: findByField(RealmHealthExamination::class.java, "userId", userId)
    }

    override suspend fun getHealthExaminationById(id: String): RealmHealthExamination? {
        return findByField(RealmHealthExamination::class.java, "_id", id)
    }

    override suspend fun addOrUpdate(healthExamination: RealmHealthExamination) {
        save(healthExamination)
    }
}
