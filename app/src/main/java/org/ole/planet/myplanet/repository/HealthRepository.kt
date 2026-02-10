package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser

interface HealthRepository {
    suspend fun getHealthExamination(userId: String): RealmHealthExamination?
    suspend fun saveExamination(examination: RealmHealthExamination)
    suspend fun getUserHealthProfile(userId: String): RealmMyHealth?
    suspend fun getExaminationById(id: String): RealmHealthExamination?
    suspend fun ensureUserKeys(userId: String): RealmUser?
}
