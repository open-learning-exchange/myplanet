package org.ole.planet.myplanet.ui.myhealth

import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel

data class HealthRecord(
    val healthPojo: RealmMyHealthPojo,
    val healthProfile: RealmMyHealth,
    val examinations: List<RealmMyHealthPojo>,
    val userMap: Map<String, RealmUserModel>
)
