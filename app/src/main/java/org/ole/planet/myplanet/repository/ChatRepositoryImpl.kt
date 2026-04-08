package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.RealmList
import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.utils.JsonUtils

class ChatRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher
) : RealmRepository(databaseService, realmDispatcher), ChatRepository {

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
