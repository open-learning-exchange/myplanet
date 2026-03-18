package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser

interface HealthRepository {
    suspend fun getHealthEntry(userId: String): Pair<RealmUser?, RealmHealthExamination?>
    suspend fun getExaminationById(id: String): RealmHealthExamination?
    suspend fun initHealth(): RealmMyHealth
    suspend fun saveExamination(examination: RealmHealthExamination?, pojo: RealmHealthExamination?, user: RealmUser?)

    suspend fun getHealthProfile(userId: String): RealmMyHealth?
    suspend fun updateUserHealthProfile(userId: String, userData: Map<String, Any?>)
    suspend fun getHealthRecordsAndAssociatedUsers(
        userId: String,
        currentUser: RealmUser
    ): HealthRecord?
}
