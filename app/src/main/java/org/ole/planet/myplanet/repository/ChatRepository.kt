package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmChatHistory

interface ChatRepository {
    suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory>
    suspend fun getLatestRev(id: String): String?
    suspend fun saveNewChat(chat: JsonObject)
    suspend fun continueConversation(id: String, query: String, response: String, rev: String)
    fun insertChatHistoryBatch(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
}
