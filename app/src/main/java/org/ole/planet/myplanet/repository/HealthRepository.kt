package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo

interface HealthRepository {
    suspend fun getHealthRecords(userId: String?): RealmMyHealthPojo?
    suspend fun getExaminations(userKey: String?): List<RealmMyHealthPojo>
}