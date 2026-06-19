package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.RealmList
import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ChatApiService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatRequest
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.model.ContentData
import org.ole.planet.myplanet.model.ContinueChatRequest
import org.ole.planet.myplanet.model.Data
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.JsonUtils

class ChatRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val chatApiService: ChatApiService,
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager
) : RealmRepository(databaseService, realmDispatcher), ChatRepository {

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
                    addProperty("createdDate", java.util.Date().time)
                    addProperty("updatedDate", java.util.Date().time)
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

    override suspend fun fetchAiProviders(serverUrl: String, isServerReachable: suspend (String) -> Boolean): Map<String, Boolean>? {
        val mapping = serverUrlMapper.processUrl(serverUrl)
        serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
            isServerReachable(url)
        }
        return chatApiService.fetchAiProviders()
    }

    override suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory> {
        if (userName.isNullOrEmpty()) {
            return emptyList()
        }
        return queryList(RealmChatHistory::class.java) {
            equalTo("user", userName)
            sort("id", Sort.DESCENDING)
        }
    }

    override suspend fun getLatestRev(id: String): String? {
        return withRealm { realm ->
            realm.where(RealmChatHistory::class.java)
                .equalTo("_id", id)
                .findAll()
                .maxByOrNull { rev -> rev._rev?.split("-")?.get(0)?.toIntOrNull() ?: 0 }
                ?._rev
        }
    }

    private suspend fun saveNewChat(chat: JsonObject) {
        executeTransaction { realm ->
            insertChatsBatchInternal(realm, listOf(chat))
        }
    }

    private suspend fun continueConversation(id: String, query: String, response: String, rev: String) {
        executeTransaction { realm ->
            addConversation(realm, id, query, response, rev)
        }
    }

    override suspend fun insertChatHistoryList(chats: List<JsonObject>) {
        executeTransaction { realm ->
            insertChatsBatchInternal(realm, chats)
        }
    }

    override fun insertChatHistoryBatch(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        bulkInsertFromSync(realm, jsonArray)
    }

    override fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val docs = mutableListOf<JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                docs.add(jsonDoc)
            }
        }
        insertChatsBatchInternal(realm, docs)
    }

    private fun insertChatsBatchInternal(realm: io.realm.Realm, chats: List<JsonObject>) {
        if (chats.isEmpty()) return

        val chatIds = chats.mapNotNull { JsonUtils.getString("_id", it) }.toTypedArray()

        // Find existing chats to delete orphaned conversations
        val existingChats = realm.where(RealmChatHistory::class.java)
            .`in`("_id", chatIds)
            .findAll()

        existingChats.forEach { chat ->
            chat.conversations?.deleteAllFromRealm()
        }

        val unmanagedChats = chats.map { json ->
            val chatHistoryId = JsonUtils.getString("_id", json)
            RealmChatHistory().apply {
                id = chatHistoryId
                _id = chatHistoryId
                _rev = JsonUtils.getString("_rev", json)
                title = JsonUtils.getString("title", json)
                createdDate = "${JsonUtils.getLong("createdDate", json)}"
                updatedDate = "${JsonUtils.getLong("updatedDate", json)}"
                user = JsonUtils.getString("user", json)
                aiProvider = JsonUtils.getString("aiProvider", json)
                val conversationsArray = JsonUtils.getJsonArray("conversations", json)
                val unmanagedConversations = conversationsArray.map {
                    JsonUtils.gson.fromJson(it, RealmConversation::class.java)
                }
                conversations = io.realm.RealmList<RealmConversation>().apply {
                    addAll(unmanagedConversations)
                }
                lastUsed = java.util.Date().time
            }
        }

        realm.insertOrUpdate(unmanagedChats)
    }

    private fun addConversation(realm: io.realm.Realm, chatHistoryId: String?, query: String?, response: String?, newRev: String?) {
        val chatHistory = realm.where(RealmChatHistory::class.java).equalTo("_id", chatHistoryId).findFirst()
        if (chatHistory != null) {
            if (chatHistory.conversations == null) {
                chatHistory.conversations = io.realm.RealmList()
            }
            val conversation = realm.createObject(RealmConversation::class.java)
            conversation.query = query
            conversation.response = response
            chatHistory.conversations?.add(conversation)
            chatHistory.updatedDate = "${Date().time}"
            chatHistory.lastUsed = Date().time
            if (!newRev.isNullOrEmpty()) {
                chatHistory._rev = newRev
            }
        }
    }
}
