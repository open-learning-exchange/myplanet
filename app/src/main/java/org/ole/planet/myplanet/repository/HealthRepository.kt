package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser

interface HealthRepository {
    suspend fun getHealthEntry(userId: String): Pair<RealmUser?, RealmHealthExamination?>
    suspend fun getExaminationById(id: String): RealmHealthExamination?
    fun initHealth(): RealmMyHealth
    suspend fun saveExamination(examination: RealmHealthExamination?, pojo: RealmHealthExamination?, user: RealmUser?)
}
