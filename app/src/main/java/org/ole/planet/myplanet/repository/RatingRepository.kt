package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface RatingRepository {
    suspend fun getRatings(type: String, userId: String?): Map<String?, JsonObject>
}
