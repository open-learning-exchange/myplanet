package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.MyLife

interface LifeRepository {
    suspend fun updateVisibility(isVisible: Boolean, myLifeId: String)
    suspend fun updateMyLifeListOrder(list: List<MyLife>)
    suspend fun getMyLifeByUserId(userId: String?, ensureLatest: Boolean = false): List<MyLife>
    suspend fun getVisibleMyLifeByUserId(userId: String?, ensureLatest: Boolean = false): List<MyLife>
    suspend fun getMyLifeForDashboard(userId: String, seedBase: List<MyLife>): List<MyLife>
    suspend fun seedMyLifeIfEmpty(userId: String?, items: List<MyLife>)
}
