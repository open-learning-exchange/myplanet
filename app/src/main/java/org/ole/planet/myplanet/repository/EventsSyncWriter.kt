package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface EventsSyncWriter {
    suspend fun insertMeetupsFromSync(docs: List<JsonObject>)
    suspend fun batchInsertMeetups(documents: List<JsonObject>): Int
}
