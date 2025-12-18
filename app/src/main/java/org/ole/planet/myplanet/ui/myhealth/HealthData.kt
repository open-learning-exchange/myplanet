package org.ole.planet.myplanet.ui.myhealth

import org.ole.planet.myplanet.model.RealmMyHealth

data class HealthData(
    val myHealth: RealmMyHealth?,
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val email: String?,
    val phoneNumber: String?,
    val dob: String?,
    val birthPlace: String?
)
