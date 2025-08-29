package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmRating

interface RatingRepository {
    suspend fun getRatings(type: String, userId: String?): Map<String, Int>
    suspend fun getUserRating(type: String?, userId: String?, itemId: String?): RealmRating?
    suspend fun saveRating(
        type: String?,
        itemId: String?,
        title: String?,
        userId: String?,
        comment: String,
        rating: Int
    )
}
