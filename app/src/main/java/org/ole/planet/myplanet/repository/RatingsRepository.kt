package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmRating

interface RatingsRepository {
    suspend fun getRatings(type: String?, userId: String?): HashMap<String?, com.google.gson.JsonObject>
    suspend fun getRatingsById(type: String, resourceId: String?, userId: String?): com.google.gson.JsonObject?
    suspend fun getCourseRatings(userId: String?): HashMap<String?, com.google.gson.JsonObject>
    suspend fun getResourceRatings(userId: String?): HashMap<String?, com.google.gson.JsonObject>
    suspend fun getRatingSummary(type: String, itemId: String, userId: String): RatingSummary

    suspend fun submitRating(
        type: String,
        itemId: String,
        title: String,
        userId: String,
        rating: Float,
        comment: String,
    ): RatingSummary

    suspend fun insertRatingsList(ratings: List<JsonObject>)
    suspend fun insertFromJson(act: JsonObject)
    fun serializeRating(realmRating: RealmRating): JsonObject
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
