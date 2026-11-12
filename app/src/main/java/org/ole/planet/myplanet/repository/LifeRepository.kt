package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.MyLife

interface LifeRepository {
    fun observeMyLifeByUserId(userId: String?): Flow<List<MyLife>>
    suspend fun updateVisibility(isVisible: Boolean, myLifeId: String, userId: String?): List<MyLife>
    suspend fun updateMyLifeListOrder(list: List<MyLife>, userId: String?)
    suspend fun getMyLifeByUserId(userId: String?, defaultItems: List<MyLife> = emptyList()): List<MyLife>
    suspend fun getMyLifeForDashboard(userId: String, seedBase: List<MyLife>): List<MyLife>
    suspend fun seedMyLifeIfEmpty(userId: String?, items: List<MyLife>): List<MyLife>
}
