package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmRating

interface RatingRepository {
    suspend fun getRatings(type: String, userId: String?): Map<String, Int>

    suspend fun getRating(
        type: String,
        itemId: String,
        userId: String?
    ): RealmRating?

    suspend fun getRatingSummary(type: String, itemId: String): Pair<Float, Int>

    suspend fun submitRating(
        type: String,
        itemId: String,
        title: String,
        userId: String,
        rating: Float,
        comment: String
    )
}
