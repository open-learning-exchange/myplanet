package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface CommunitySyncRepository {
    suspend fun insertMeetupsFromSync(docs: List<JsonObject>)
}
