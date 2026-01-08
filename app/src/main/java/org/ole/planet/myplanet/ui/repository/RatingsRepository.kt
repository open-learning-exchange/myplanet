package org.ole.planet.myplanet.ui.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.repository.RatingSummary

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

    suspend fun getRatings(type: String, userId: String?): HashMap<String?, JsonObject>
}