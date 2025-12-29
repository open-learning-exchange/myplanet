package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLife

interface LifeRepository {
    suspend fun updateVisibility(isVisible: Boolean, myLifeId: String)
    suspend fun updateMyLifeListOrder(list: List<RealmMyLife>)
}
