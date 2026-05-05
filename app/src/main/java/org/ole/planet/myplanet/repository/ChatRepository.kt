package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.model.RealmChatHistory
import retrofit2.Response

interface ChatRepository {
    suspend fun sendNewChatRequest(query: String, user: String?, aiProvider: AiProvider): Response<ChatResponse>
    suspend fun sendContinueChatRequest(message: String, user: String?, aiProvider: AiProvider, id: String, rev: String): Response<ChatResponse>
    suspend fun fetchAiProviders(serverUrl: String, isServerReachable: suspend (String) -> Boolean): Map<String, Boolean>?
    suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory>
    suspend fun getLatestRev(id: String): String?
    suspend fun saveNewChat(chat: JsonObject)
    suspend fun continueConversation(id: String, query: String, response: String, rev: String)
    suspend fun insertChatHistoryList(chats: List<JsonObject>)
    fun insertChatHistoryBatch(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
    suspend fun fetchAndSaveUserChatHistory(userName: String): Boolean
    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
}
