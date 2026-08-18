package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface EventsSyncHandler {
    suspend fun batchInsertMeetups(documents: List<JsonObject>): Int
}
