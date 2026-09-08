package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface ChatSyncWriter {
    suspend fun insertChatHistoryFromSync(docs: List<JsonObject>)
    suspend fun deleteByIds(ids: List<String>)
}
