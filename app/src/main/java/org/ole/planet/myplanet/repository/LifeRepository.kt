package org.ole.planet.myplanet.repository

interface LifeRepository {
    suspend fun updateVisibility(isVisible: Boolean, myLifeId: String)
    suspend fun updateWeight(weight: Int, id: String?, userId: String?)
}
