package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.myhealth.MyHealthProfile

interface HealthRepository {
    suspend fun getMyHealthProfile(userId: String, user: RealmUserModel): MyHealthProfile?
}
