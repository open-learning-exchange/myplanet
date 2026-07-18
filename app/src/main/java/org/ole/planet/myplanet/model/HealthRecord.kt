package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser

data class HealthRecord(
    val healthPojo: HealthExamination,
    val healthProfile: RealmMyHealth,
    val examinations: List<HealthExamination>,
    val userMap: Map<String, RealmUser>
)
