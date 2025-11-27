package org.ole.planet.myplanet.repository

interface LifeRepository {
    suspend fun updateVisibility(isVisible: Boolean, myLifeId: String)
    suspend fun updateAllWeights(orderedIds: List<String>)
}
