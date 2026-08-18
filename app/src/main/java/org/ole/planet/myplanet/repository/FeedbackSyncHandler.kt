package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface FeedbackSyncHandler {
    suspend fun insertFeedbackList(jsonObjects: List<JsonObject>)
}
