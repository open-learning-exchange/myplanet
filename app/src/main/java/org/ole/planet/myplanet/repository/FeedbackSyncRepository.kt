package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface FeedbackSyncRepository {
    suspend fun insertFeedbackList(jsonObjects: List<JsonObject>)
}
