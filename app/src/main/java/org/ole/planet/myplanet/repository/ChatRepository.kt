package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.model.RealmChatHistory

sealed class ChatResult {
    data class Success(val response: String, val id: String, val rev: String) : ChatResult()
    data class Error(val message: String) : ChatResult()
}

interface ChatRepository {
    suspend fun sendNewChatRequest(query: String, user: String?, aiProvider: AiProvider): ChatResult
    suspend fun sendContinueChatRequest(message: String, user: String?, aiProvider: AiProvider, id: String, rev: String): ChatResult
    suspend fun fetchAiProviders(serverUrl: String, isServerReachable: suspend (String) -> Boolean): Map<String, Boolean>?
    suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory>
    suspend fun getLatestRev(id: String): String?
    suspend fun insertChatHistoryList(chats: List<JsonObject>)
    fun insertChatHistoryBatch(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
}
