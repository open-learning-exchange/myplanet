package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLife

interface MyLifeRepository {
    suspend fun getMyLifeByUserId(userId: String?): List<RealmMyLife>
    suspend fun updateWeight(weight: Int, id: String?, userId: String?)
    suspend fun updateVisibility(isVisible: Boolean, id: String?)
}
