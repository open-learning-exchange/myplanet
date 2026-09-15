package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface CommunitySyncWriter {
    suspend fun insertMeetupsFromSync(docs: List<JsonObject>)
}
