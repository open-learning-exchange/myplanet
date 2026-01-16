package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUserModel

interface HealthRepository {
    suspend fun getExaminationById(id: String): RealmHealthExamination?
    suspend fun getExaminationByUserId(userId: String): RealmHealthExamination?
    suspend fun getUserById(id: String): RealmUserModel?
    suspend fun saveExamination(examination: RealmHealthExamination, healthRecord: RealmHealthExamination)
}
