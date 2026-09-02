package org.ole.planet.myplanet.repository

import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.data.api.ChatApiService
import org.ole.planet.myplanet.data.room.dao.ChatDao
import org.ole.planet.myplanet.di.PlainGson
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
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Utilities

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val chatApiService: ChatApiService,
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager,
    private val dispatcherProvider: DispatcherProvider,
    @PlainGson private val gson: Gson
) : ChatRepository, ChatSyncWriter {

    private data class PrecomputedChat(
        val chat: ChatHistory,
        val normalizedTitle: String?,
        val normalizedQueries: List<String?>,
        val normalizedResponses: List<String?>
    )

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
            val jsonContent = gson.toJson(chatData)
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
            val jsonContent = gson.toJson(continueChatData)
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

    private suspend fun buildPrecomputedChats(chats: List<ChatHistory>): List<PrecomputedChat> = withContext(dispatcherProvider.default) {
        chats.map { chat ->
            val title = if (chat.conversations != null && chat.conversations?.isNotEmpty() == true) {
                chat.conversations?.get(0)?.query?.let { Utilities.normalizeText(it) }
            } else {
                chat.title?.let { Utilities.normalizeText(it) }
            }
            val queries = chat.conversations?.map { it?.query?.let { q -> Utilities.normalizeText(q) } } ?: emptyList()
            val responses = chat.conversations?.map { it?.response?.let { r -> Utilities.normalizeText(r) } } ?: emptyList()
            PrecomputedChat(chat, title, queries, responses)
        }
    }

    override suspend fun searchChats(query: String, mode: ChatSearchMode, chats: List<ChatHistory>): List<ChatHistory> {
        val precomputedChats = buildPrecomputedChats(chats)
        return if (mode == ChatSearchMode.TITLE) {
            searchByTitle(query, precomputedChats)
        } else {
            fullConvoSearch(query, isQuestion = (mode == ChatSearchMode.QUESTION), precomputedChats)
        }
    }

    private suspend fun fullConvoSearch(s: String, isQuestion: Boolean, precomputedChats: List<PrecomputedChat>): List<ChatHistory> = withContext(dispatcherProvider.default) {
        var conversation: String?
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val inTitleStartQuery = mutableListOf<ChatHistory>()
        val inTitleContainsQuery = mutableListOf<ChatHistory>()
        val startsWithQuery = mutableListOf<ChatHistory>()
        val containsQuery = mutableListOf<ChatHistory>()
        for (pChat in precomputedChats) {
            val conversations = pChat.chat.conversations
            if (!conversations.isNullOrEmpty()) {
                for (i in 0 until conversations.size) {
                    conversation = if (isQuestion) {
                        pChat.normalizedQueries[i]
                    } else {
                        pChat.normalizedResponses[i]
                    }
                    if (conversation == null) continue
                    if (conversation.startsWith(normalizedQuery, ignoreCase = true)) {
                        if (i == 0) inTitleStartQuery.add(pChat.chat) else startsWithQuery.add(pChat.chat)
                        break
                    } else if (normalizedQueryParts.all { conversation.contains(it, ignoreCase = true) }) {
                        if (i == 0) inTitleContainsQuery.add(pChat.chat) else containsQuery.add(pChat.chat)
                        break
                    }
                }
            }
        }
        inTitleStartQuery + inTitleContainsQuery + startsWithQuery + containsQuery
    }

    private suspend fun searchByTitle(s: String, precomputedChats: List<PrecomputedChat>): List<ChatHistory> = withContext(dispatcherProvider.default) {
        var title: String?
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val startsWithQuery = mutableListOf<ChatHistory>()
        val containsQuery = mutableListOf<ChatHistory>()
        for (pChat in precomputedChats) {
            title = pChat.normalizedTitle
            if (title == null) continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(pChat.chat)
            } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                containsQuery.add(pChat.chat)
            }
        }
        startsWithQuery + containsQuery
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
                    gson.fromJson(it, Conversation::class.java)
                }
                lastUsed = Date().time
            }
        }
        chatDao.upsertAll(entities)
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
                            val array = gson.fromJson(news.viewIn, JsonArray::class.java)
                            val list = mutableListOf<String>()
                            for (i in 0 until array.size()) {
                                val elem = array.get(i) as JsonElement
                                if (elem.isJsonObject) {
                                    val id = elem.asJsonObject.get("_id")?.asString
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
