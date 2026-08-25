package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface FeedbackSyncWriter {
    suspend fun insertFeedbackList(jsonObjects: List<JsonObject>)
}
