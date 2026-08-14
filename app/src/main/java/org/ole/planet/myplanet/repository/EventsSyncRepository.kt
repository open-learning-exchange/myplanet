package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface EventsSyncRepository {
    suspend fun batchInsertMeetups(documents: List<JsonObject>): Int
}
