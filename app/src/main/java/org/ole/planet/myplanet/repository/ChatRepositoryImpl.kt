package org.ole.planet.myplanet.repository

import androidx.annotation.VisibleForTesting
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Date
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.data.api.ChatApiService
import org.ole.planet.myplanet.data.room.dao.ChatDao
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatRequest
import org.ole.planet.myplanet.model.ContentData
import org.ole.planet.myplanet.model.ContinueChatRequest
import org.ole.planet.myplanet.model.Data
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.JsonUtils

class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val chatApiService: ChatApiService,
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager
) : ChatRepository {

    @VisibleForTesting
    internal var reachabilityCheck: suspend (String) -> Boolean = { url ->
        org.ole.planet.myplanet.MainApplication.isServerReachable(url)
    }

    override suspend fun sendNewChatRequest(
        query: String,
        user: String?,
        aiProvider: AiProvider
    ): ChatResult {
        return try {
            val chatData = ChatRequest(data = ContentData(user ?: "", query, aiProvider), save = true)
            val jsonContent = JsonUtils.gson.toJson(chatData)
            val requestBody = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())
            val response = chatApiService.sendChatRequest(requestBody)
            val responseBody = response.body()
            if (response.isSuccessful && responseBody != null && responseBody.status == "Success") {
                val chatResponse = responseBody.chat ?: ""
                val id = responseBody.couchDBResponse?.id ?: ""
                val rev = responseBody.couchDBResponse?.rev ?: ""
                val jsonObject = JsonObject().apply {
                    addProperty("_rev", rev)
                    addProperty("_id", id)
                    addProperty("aiProvider", aiProvider.name)
                    addProperty("user", user)
                    addProperty("title", query)
                    addProperty("createdDate", Date().time)
                    addProperty("updatedDate", Date().time)
                    val conversationsArray = JsonArray()
                    val conversationObject = JsonObject().apply {
                        addProperty("query", query)
                        addProperty("response", chatResponse)
                    }
                    conversationsArray.add(conversationObject)
                    add("conversations", conversationsArray)
                }
                saveNewChat(jsonObject)
                ChatResult.Success(chatResponse, id, rev)
            } else {
                ChatResult.Error(responseBody?.message ?: response.message() ?: "Request failed")
            }
        } catch (e: Exception) {
            ChatResult.Error(e.message ?: "Request failed")
        }
    }

    override suspend fun sendContinueChatRequest(
        message: String,
        user: String?,
        aiProvider: AiProvider,
        id: String,
        rev: String
    ): ChatResult {
        return try {
            val continueChatData = ContinueChatRequest(data = Data(user ?: "", message, aiProvider, id, rev), save = true)
            val jsonContent = JsonUtils.gson.toJson(continueChatData)
            val requestBody = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())
            val response = chatApiService.sendChatRequest(requestBody)
            val responseBody = response.body()
            if (response.isSuccessful && responseBody != null && responseBody.status == "Success") {
                val chatResponse = responseBody.chat ?: ""
                val newRev = responseBody.couchDBResponse?.rev ?: rev
                continueConversation(id, message, chatResponse, newRev)
                ChatResult.Success(chatResponse, id, newRev)
            } else {
                continueConversation(id, message, "", rev)
                ChatResult.Error(responseBody?.message ?: response.message() ?: "Request failed")
            }
        } catch (e: Exception) {
            continueConversation(id, message, "", rev)
            ChatResult.Error(e.message ?: "Request failed")
        }
    }

    override suspend fun fetchAiProviders(serverUrl: String): Map<String, Boolean>? {
        val mapping = serverUrlMapper.processUrl(serverUrl)
        serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
            reachabilityCheck(url)
        }
        return chatApiService.fetchAiProviders()
    }

    override suspend fun getChatHistoryForUser(userName: String?): List<ChatHistory> {
        if (userName.isNullOrEmpty()) {
            return emptyList()
        }
        return chatDao.getByUser(userName)
    }

    override suspend fun getLatestRev(id: String): String? {
        return chatDao.getByDocId(id)
            .maxByOrNull { rev -> rev._rev?.split("-")?.get(0)?.toIntOrNull() ?: 0 }
            ?._rev
    }

    private suspend fun saveNewChat(chat: JsonObject) {
        insertChatsBatchInternal(listOf(chat))
    }

    private suspend fun continueConversation(id: String, query: String, response: String, rev: String) {
        addConversation(id, query, response, rev)
    }

    override suspend fun insertChatHistoryList(chats: List<JsonObject>) {
        insertChatsBatchInternal(chats)
    }

    override suspend fun insertChatHistoryFromSync(docs: List<JsonObject>) {
        val unwrappedDocs = mutableListOf<JsonObject>()
        for (j in docs) {
            val jsonDoc = JsonUtils.getJsonObject("doc", j)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                unwrappedDocs.add(jsonDoc)
            }
        }
        insertChatsBatchInternal(unwrappedDocs)
    }

    private suspend fun insertChatsBatchInternal(chats: List<JsonObject>) {
        if (chats.isEmpty()) return
        // @Insert(REPLACE) upserts by primary key, replacing the whole row (including the embedded
        // conversations JSON), which subsumes the old "delete orphaned conversations" step.
        val entities = chats.map { json ->
            val chatHistoryId = JsonUtils.getString("_id", json)
            ChatHistory().apply {
                id = chatHistoryId
                _id = chatHistoryId
                _rev = JsonUtils.getString("_rev", json)
                title = JsonUtils.getString("title", json)
                createdDate = "${JsonUtils.getLong("createdDate", json)}"
                updatedDate = "${JsonUtils.getLong("updatedDate", json)}"
                user = JsonUtils.getString("user", json)
                aiProvider = JsonUtils.getString("aiProvider", json)
                val conversationsArray = JsonUtils.getJsonArray("conversations", json)
                conversations = conversationsArray.map {
                    JsonUtils.gson.fromJson(it, RealmConversation::class.java)
                }
                lastUsed = Date().time
            }
        }
        chatDao.upsertAll(entities)
    }

    private suspend fun addConversation(chatHistoryId: String?, query: String?, response: String?, newRev: String?) {
        if (chatHistoryId == null) return
        val chatHistory = chatDao.findByDocId(chatHistoryId) ?: return
        val conversation = RealmConversation().apply {
            this.query = query
            this.response = response
        }
        chatHistory.conversations = (chatHistory.conversations ?: emptyList()) + conversation
        chatHistory.updatedDate = "${Date().time}"
        chatHistory.lastUsed = Date().time
        if (!newRev.isNullOrEmpty()) {
            chatHistory._rev = newRev
        }
        chatDao.update(chatHistory)
    }
}
