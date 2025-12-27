package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmVoices

interface ChatRepository {
    suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory>
    suspend fun getPlanetVoicesMessages(planetCode: String?): List<RealmVoices>
    suspend fun getLatestRev(id: String): String?
    suspend fun saveNewChat(chat: JsonObject)
    suspend fun continueConversation(id: String, query: String, response: String, rev: String)
}
