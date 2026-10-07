package org.ole.planet.myplanet.repository

import androidx.annotation.VisibleForTesting
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.data.api.ChatApiService
import org.ole.planet.myplanet.data.room.dao.ChatDao
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.ChatRequest
import org.ole.planet.myplanet.model.ContentData
import org.ole.planet.myplanet.model.ContinueChatRequest
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.Data
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.ServerReachabilityProvider

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val chatApiService: ChatApiService,
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager,
    private val dispatcherProvider: DispatcherProvider,
    private val serverReachabilityProvider: ServerReachabilityProvider
) : ChatRepository, ChatSyncWriter {

    @VisibleForTesting
    internal var reachabilityCheck: suspend (String) -> Boolean = { url ->
        serverReachabilityProvider.isServerReachable(url)
    }

    override suspend fun sendNewChatRequest(
        query: String,
        user: String?,
        aiProvider: AiProvider
    ): ChatResult {
        return try {
            val chatData = ChatRequest(data = ContentData(user ?: "", query, aiProvider), save = true)
            // The AI chat body keeps the exact bytes Gson().toJson wrote (HTML-safe escapes).
            val jsonContent = KJson.gsonToJson(Json.encodeToJsonElement(ChatRequest.serializer(), chatData))
            val requestBody = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())
            val response = chatApiService.sendChatRequest(requestBody)
            val responseBody = response.body()
            if (response.isSuccessful && responseBody != null && responseBody.status == "Success") {
                val chatResponse = responseBody.chat ?: ""
                val id = responseBody.couchDBResponse?.id ?: ""
                val rev = responseBody.couchDBResponse?.rev ?: ""
                val jsonObject = buildJsonObject {
                    put("_rev", rev)
                    put("_id", id)
                    put("aiProvider", aiProvider.name)
                    put("user", user)
                    put("title", query)
                    put("createdDate", Date().time)
                    put("updatedDate", Date().time)
                    val conversationObject = buildJsonObject {
                        put("query", query)
                        put("response", chatResponse)
                    }
                    put("conversations", JsonArray(listOf(conversationObject)))
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
            val jsonContent = KJson.gsonToJson(Json.encodeToJsonElement(ContinueChatRequest.serializer(), continueChatData))
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
        val chats = chatDao.getByUser(userName)
        return sortChats(chats)
    }

    private fun sortChats(chats: List<ChatHistory>): List<ChatHistory> {
        return chats.sortedByDescending { chat ->
            maxOf(chat.createdDate?.toLongOrNull() ?: 0L, chat.updatedDate?.toLongOrNull() ?: 0L)
        }
    }

    override suspend fun getLatestRev(id: String): String? {
        return chatDao.getRevsByDocId(id)
            .maxByOrNull { rev -> rev?.split("-")?.get(0)?.toIntOrNull() ?: 0 }
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
            val jsonDoc = KJson.getJsonObject("doc", j)
            val id = KJson.getString("_id", jsonDoc)
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
            val chatHistoryId = KJson.getString("_id", json)
            ChatHistory().apply {
                id = chatHistoryId
                _id = chatHistoryId
                _rev = KJson.getString("_rev", json)
                title = KJson.getString("title", json)
                createdDate = "${KJson.getLong("createdDate", json)}"
                updatedDate = "${KJson.getLong("updatedDate", json)}"
                user = KJson.getString("user", json)
                aiProvider = KJson.getString("aiProvider", json)
                val conversationsArray = KJson.getJsonArray("conversations", json)
                conversations = conversationsArray.mapNotNull(::conversationFrom)
                lastUsed = Date().time
            }
        }
        chatDao.upsertAll(entities)
    }

    /**
     * What Gson's reflective read made of one conversation element: `null` for JSON null (now
     * dropped from the list), `query`/`response` from any primitive's text, unknown keys ignored,
     * and an exception for anything that is not an object or a string field holding a container.
     */
    private fun conversationFrom(element: JsonElement): Conversation? {
        if (element is JsonNull) return null
        val obj = element as? JsonObject ?: throw IllegalStateException("Expected BEGIN_OBJECT but was $element")
        return Conversation().apply {
            query = conversationText(obj["query"])
            response = conversationText(obj["response"])
        }
    }

    private fun conversationText(element: JsonElement?): String? = when (element) {
        null, is JsonNull -> null
        is JsonPrimitive -> KJson.gsonAsString(element)
        else -> throw IllegalStateException("Expected a string but was $element")
    }

    private suspend fun addConversation(chatHistoryId: String?, query: String?, response: String?, newRev: String?) {
        if (chatHistoryId == null) return
        val chatHistory = chatDao.findByDocId(chatHistoryId) ?: return
        val conversation = Conversation().apply {
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

    override fun extractSharedViewInIds(sharedNews: List<News>): Map<String, Set<String>> {
        if (sharedNews.isEmpty()) return emptyMap()
        return sharedNews
            .groupBy { it.newsId }
            .mapNotNull { (newsId, newsEntries) ->
                if (newsId == null) null
                else {
                    val ids = newsEntries.flatMap { news ->
                        try {
                            val array = KJson.parseArrayOrNull(news.viewIn) ?: throw NullPointerException("blank viewIn")
                            val list = mutableListOf<String>()
                            for (elem in array) {
                                if (elem is JsonObject) {
                                    val id = elem["_id"]?.let { KJson.gsonAsString(it, KJson.GsonNumbers.LITERAL) }
                                    if (id != null) list.add(id)
                                }
                            }
                            list
                        } catch (_: Exception) {
                            emptyList<String>()
                        }
                    }.toSet()
                    newsId to ids
                }
            }
            .toMap()
    }
}
