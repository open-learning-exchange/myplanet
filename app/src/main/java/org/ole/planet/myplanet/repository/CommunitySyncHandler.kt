package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface CommunitySyncHandler {
    suspend fun insertMeetupsFromSync(docs: List<JsonObject>)
}
