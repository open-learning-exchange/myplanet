package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.UserEntity

interface RatingsRepository {
    suspend fun getRatings(type: String?, userId: String?): HashMap<String?, JsonObject>
    suspend fun getRatingsById(type: String, resourceId: String?, userId: String?): RatingSummary?
    suspend fun getCourseRatings(userId: String?): HashMap<String?, JsonObject>
    suspend fun getResourceRatings(userId: String?): HashMap<String?, JsonObject>
    suspend fun getRatingSummary(type: String, itemId: String, userId: String?): RatingSummary
    suspend fun isRatingPrompted(userId: String, resourceId: String): Boolean
    suspend fun setRatingPrompted(userId: String, resourceId: String)

    suspend fun submitRating(
        type: String,
        itemId: String,
        title: String,
        user: UserEntity,
        rating: Float,
        comment: String,
    ): RatingSummary
    suspend fun insertRatingsFromSync(documentList: List<JsonObject>)
    suspend fun getPendingRatingUploads(): List<org.ole.planet.myplanet.model.Rating>
    suspend fun markRatingUploaded(id: String): Boolean
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
