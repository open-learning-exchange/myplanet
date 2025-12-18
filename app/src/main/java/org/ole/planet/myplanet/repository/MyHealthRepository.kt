package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.ui.myhealth.HealthData

interface MyHealthRepository {
    suspend fun getHealthData(userId: String?): HealthData
    suspend fun saveHealthData(userId: String?, health: RealmMyHealth, healthData: HealthData)
}
