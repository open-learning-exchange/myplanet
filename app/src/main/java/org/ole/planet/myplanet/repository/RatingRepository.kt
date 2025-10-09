package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface RatingRepository {
    suspend fun getRatingSummary(type: String, itemId: String, userId: String): RatingSummary

    suspend fun submitRating(
        type: String,
        itemId: String,
        title: String,
        userId: String,
        userMetadata: RatingUserMetadata? = null,
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

data class RatingUserMetadata(
    val primaryId: String?,
    val legacyId: String?,
    val parentCode: String?,
    val planetCode: String?,
    val serialized: JsonObject,
) {
    fun resolvedUserId(): String? =
        primaryId?.takeIf { it.isNotBlank() }
            ?: legacyId?.takeIf { it.isNotBlank() }
}
