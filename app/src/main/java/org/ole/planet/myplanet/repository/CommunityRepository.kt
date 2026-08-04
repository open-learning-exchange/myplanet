package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.Community

interface CommunityRepository {
    suspend fun replaceAll(rows: JsonArray)
    suspend fun getAllSorted(): List<Community>
    suspend fun syncCommunityDocs(): Boolean
    suspend fun insertMeetupsFromSync(docs: List<JsonObject>)
}
