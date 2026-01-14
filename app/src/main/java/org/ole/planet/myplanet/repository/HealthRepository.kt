package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.RealmUserModel

interface HealthRepository {
    suspend fun getHealthData(userId: String, currentUser: RealmUserModel): HealthRecord?
}
