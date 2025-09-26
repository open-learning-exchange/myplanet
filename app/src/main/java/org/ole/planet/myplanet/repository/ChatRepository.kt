package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmChatHistory

interface ChatRepository {
    suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory>
    suspend fun getLatestRevision(chatId: String): String?
    suspend fun insertChatHistory(chatHistory: JsonObject?)
    suspend fun appendConversationToHistory(
        chatId: String,
        query: String?,
        response: String?,
        newRev: String?,
    )
}
