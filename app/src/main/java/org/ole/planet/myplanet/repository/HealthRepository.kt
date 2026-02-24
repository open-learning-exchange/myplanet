package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUser

interface HealthRepository {
    suspend fun getHealthEntry(userId: String): Pair<RealmUser?, RealmHealthExamination?>
    suspend fun getExaminationById(id: String): RealmHealthExamination?
    suspend fun updateHealthUserId(localUserId: String, serverUserId: String)
    suspend fun updateHealthRev(id: String, rev: String)
}
