package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmRating

interface RatingRepository {
    suspend fun getRatings(type: String, userId: String?): Map<String, Int>
}
