package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmExamination
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser

interface HealthRepository {
    suspend fun getHealthProfile(userId: String): RealmHealthExamination?
    suspend fun getExamination(id: String): RealmHealthExamination?
    suspend fun getUser(userId: String): RealmUser?
    suspend fun ensureUserEncryptionKeys(userId: String)
    suspend fun saveExamination(
        userId: String,
        examinationId: String?,
        profile: RealmMyHealth,
        details: RealmExamination,
        examinationFields: RealmHealthExamination
    )
}
