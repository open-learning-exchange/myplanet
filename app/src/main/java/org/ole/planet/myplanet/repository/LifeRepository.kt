package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLife

interface LifeRepository {
    suspend fun getMyLifeList(userId: String?): Flow<List<RealmMyLife>>
    suspend fun setupMyLife(userId: String?)
    suspend fun updateVisibility(isVisible: Boolean, myLifeId: String)
    suspend fun updateMyLifeListOrder(list: List<RealmMyLife>)
}
