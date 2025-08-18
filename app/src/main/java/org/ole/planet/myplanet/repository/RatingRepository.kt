package org.ole.planet.myplanet.repository

interface RatingRepository {
    suspend fun getRatings(type: String, userId: String?): Map<String, Int>
}