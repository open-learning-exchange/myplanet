package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface CommunityRepository {
    suspend fun fetchCommunityRegistrationRequests(): JsonObject?
}
