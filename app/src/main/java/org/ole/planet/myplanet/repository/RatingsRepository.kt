package org.ole.planet.myplanet.repository

interface RatingsRepository {
    suspend fun getRatingSummary(type: String, itemId: String, userId: String): RatingSummary

    suspend fun submitRating(
        type: String,
        itemId: String,
        title: String,
        userId: String,
        rating: Float,
        comment: String,
    ): RatingSummary

    suspend fun rateResource(rating: Float, resourceId: String, user: org.ole.planet.myplanet.model.RealmUserModel, comment: String)
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
