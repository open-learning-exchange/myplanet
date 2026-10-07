package org.ole.planet.myplanet.repository

import kotlinx.serialization.json.JsonObject

interface FeedbackSyncWriter {
    suspend fun insertFeedbackList(jsonObjects: List<JsonObject>)
}
