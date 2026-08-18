package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface ChatSyncHandler {
    suspend fun insertChatHistoryFromSync(docs: List<JsonObject>)
}
