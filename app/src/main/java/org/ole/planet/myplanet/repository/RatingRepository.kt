package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmUserModel

interface RatingRepository {
    suspend fun getRatings(type: String, userId: String?): Map<String, Int>

    suspend fun getRatingSummary(type: String, itemId: String, userId: String): RatingSummary

    suspend fun submitRating(
        type: String,
        itemId: String,
        title: String,
        user: RealmUserModel,
        rating: Float,
        comment: String,
    ): RatingSummary
}

data class RatingEntry(
    val id: String?,
    val comment: String?,
    val rate: Int,
)

data class RatingSummary(
    val existingRating: RatingEntry?,
    val averageRating: Float,
    val totalRatings: Int,
    val userRating: Int?,
)
