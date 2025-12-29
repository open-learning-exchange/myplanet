package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmNews

interface ChatRepository {
    suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory>
    suspend fun getPlanetNewsMessages(planetCode: String?): List<RealmNews>
    suspend fun getLatestRev(id: String): String?
    suspend fun saveNewChat(chat: JsonObject)
    suspend fun continueConversation(id: String, query: String, response: String, rev: String)
}
