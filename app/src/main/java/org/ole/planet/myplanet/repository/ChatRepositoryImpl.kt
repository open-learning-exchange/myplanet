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
import org.ole.planet.myplanet.data.api.ApiInterface
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
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

class ChatRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val chatApiService: ChatApiService,
    private val apiInterface: ApiInterface,
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager
) : RealmRepository(databaseService, realmDispatcher), ChatRepository {

    override suspend fun sendNewChatRequest(
        query: String,
        user: String?,
        aiProvider: AiProvider
    ): Response<ChatResponse> {
        val chatData = ChatRequest(data = ContentData(user ?: "", query, aiProvider), save = true)
        val jsonContent = JsonUtils.gson.toJson(chatData)
        val requestBody = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())
        return chatApiService.sendChatRequest(requestBody)
    }

    override suspend fun sendContinueChatRequest(
        message: String,
        user: String?,
        aiProvider: AiProvider,
        id: String,
        rev: String
    ): Response<ChatResponse> {
        val continueChatData = ContinueChatRequest(data = Data(user ?: "", message, aiProvider, id, rev), save = true)
        val jsonContent = JsonUtils.gson.toJson(continueChatData)
        val requestBody = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())
        return chatApiService.sendChatRequest(requestBody)
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

    override suspend fun saveNewChat(chat: JsonObject) {
        executeTransaction { realm ->
            insertChatHistory(realm, chat)
        }
    }

    override suspend fun continueConversation(id: String, query: String, response: String, rev: String) {
        executeTransaction { realm ->
            addConversation(realm, id, query, response, rev)
        }
    }

    override suspend fun insertChatHistoryList(chats: List<JsonObject>) {
        executeTransaction { realm ->
            chats.forEach { insertChatHistory(realm, it) }
        }
    }

    override suspend fun fetchAndSaveUserChatHistory(userName: String): Boolean {
        return try {
            val url = "${UrlUtils.getUrl()}/chat_history/_find"
            android.util.Log.d("ChatHistorySync", "fetch started — user=$userName url=$url")
            val selector = JsonObject().apply {
                add("selector", JsonObject().apply { addProperty("user", userName) })
            }
            val response = apiInterface.findDocs(UrlUtils.header, "application/json", url, selector)
            if (!response.isSuccessful || response.body() == null) {
                android.util.Log.w("ChatHistorySync", "fetch failed — HTTP ${response.code()}")
                return false
            }
            val docs = JsonUtils.getJsonArray("docs", response.body())
            val chatList = mutableListOf<JsonObject>()
            for (element in docs) {
                val doc = element.asJsonObject
                if (!JsonUtils.getString("_id", doc).startsWith("_design")) {
                    chatList.add(doc)
                }
            }
            android.util.Log.d("ChatHistorySync", "fetch succeeded — ${chatList.size} doc(s) for user=$userName")
            if (chatList.isNotEmpty()) insertChatHistoryList(chatList)
            true
        } catch (e: Exception) {
            android.util.Log.e("ChatHistorySync", "fetch threw exception: ${e.message}", e)
            false
        }
    }

        override fun insertChatHistoryBatch(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val docs = mutableListOf<JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            docs.add(jsonDoc)
        }
        docs.forEach { insertChatHistory(realm, it) }
    }

    private fun insertChatHistory(realm: io.realm.Realm, json: JsonObject) {
        val chatHistoryId = JsonUtils.getString("_id", json)
        val existingChatHistory = realm.where(RealmChatHistory::class.java).equalTo("_id", chatHistoryId).findFirst()
        existingChatHistory?.deleteFromRealm()
        val chatHistory = realm.createObject(RealmChatHistory::class.java, chatHistoryId)
        chatHistory._rev = JsonUtils.getString("_rev", json)
        chatHistory._id = JsonUtils.getString("_id", json)
        chatHistory.title = JsonUtils.getString("title", json)
        chatHistory.createdDate = "${JsonUtils.getLong("createdDate", json)}"
        chatHistory.updatedDate = "${JsonUtils.getLong("updatedDate", json)}"
        chatHistory.user = JsonUtils.getString("user", json)
        chatHistory.aiProvider = JsonUtils.getString("aiProvider", json)
        chatHistory.conversations = parseConversations(realm, JsonUtils.getJsonArray("conversations", json))
        chatHistory.lastUsed = Date().time
    }

    private fun parseConversations(realm: io.realm.Realm, jsonArray: JsonArray): io.realm.RealmList<RealmConversation> {
        val conversations = io.realm.RealmList<RealmConversation>()
        val unmanagedConversations = jsonArray.map { JsonUtils.gson.fromJson(it, RealmConversation::class.java) }
        conversations.addAll(realm.copyToRealm(unmanagedConversations))
        return conversations
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
