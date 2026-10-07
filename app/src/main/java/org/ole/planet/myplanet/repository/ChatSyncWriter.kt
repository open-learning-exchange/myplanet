package org.ole.planet.myplanet.repository

import kotlinx.serialization.json.JsonObject

interface ChatSyncWriter {
    suspend fun insertChatHistoryFromSync(docs: List<JsonObject>)
}
