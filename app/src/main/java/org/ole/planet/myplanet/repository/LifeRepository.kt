package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLife

interface LifeRepository {
    suspend fun getMyLife(userId: String?): List<RealmMyLife>
    suspend fun updateVisibility(isVisible: Boolean, myLifeId: String)
}
