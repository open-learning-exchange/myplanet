package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.UserEntity

data class HealthRecord(
    val healthPojo: HealthExamination,
    val healthProfile: MyHealth,
    val examinations: List<HealthExamination>,
    val userMap: Map<String, UserEntity>
)
