package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmCommunity

interface CommunityRepository {
    suspend fun getAllCommunities(): List<RealmCommunity>
    suspend fun addCommunities(communities: List<RealmCommunity>)
}
