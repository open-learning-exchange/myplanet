package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmNews

interface ChatRepository {
    suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory>
    suspend fun getPlanetNewsMessages(planetCode: String?): List<RealmNews>
    suspend fun getLatestRevision(chatId: String?): String?
    suspend fun saveChatHistory(chatHistory: JsonObject)
    suspend fun appendConversation(
        chatId: String?,
        query: String?,
        response: String?,
        newRev: String?,
    )
}
