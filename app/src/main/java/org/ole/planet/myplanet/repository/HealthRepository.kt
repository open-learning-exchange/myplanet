package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmHealthExamination

interface HealthRepository {
    suspend fun getHealthExaminationByUserId(userId: String): RealmHealthExamination?
    suspend fun getHealthExaminationById(id: String): RealmHealthExamination?
    suspend fun addOrUpdate(healthExamination: RealmHealthExamination)
}
