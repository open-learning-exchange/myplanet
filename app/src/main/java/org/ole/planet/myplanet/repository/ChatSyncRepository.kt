package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface ChatSyncRepository {
    suspend fun insertChatHistoryFromSync(docs: List<JsonObject>)
}
