package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUserModel

data class HealthRecord(
    val healthPojo: RealmHealthExamination,
    val healthProfile: RealmMyHealth,
    val examinations: List<RealmHealthExamination>,
    val userMap: Map<String, RealmUserModel>
)
