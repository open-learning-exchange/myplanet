package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.realm.RealmList
import io.realm.Sort
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
            val chatHistoryId = JsonUtils.getString("_id", chat)
            val existingChatHistory = realm.where(RealmChatHistory::class.java).equalTo("_id", chatHistoryId).findFirst()
            existingChatHistory?.deleteFromRealm()
            val chatHistory = realm.createObject(RealmChatHistory::class.java, chatHistoryId)
            chatHistory._rev = JsonUtils.getString("_rev", chat)
            chatHistory.title = JsonUtils.getString("title", chat)
            chatHistory.createdDate = "${JsonUtils.getLong("createdDate", chat)}"
            chatHistory.updatedDate = "${JsonUtils.getLong("updatedDate", chat)}"
            chatHistory.user = JsonUtils.getString("user", chat)
            chatHistory.aiProvider = JsonUtils.getString("aiProvider", chat)

            val jsonArray = JsonUtils.getJsonArray("conversations", chat)
            val conversations = RealmList<RealmConversation>()
            val unmanagedConversations = jsonArray.map { JsonUtils.gson.fromJson(it, RealmConversation::class.java) }
            conversations.addAll(realm.copyToRealm(unmanagedConversations))
            chatHistory.conversations = conversations

            chatHistory.lastUsed = System.currentTimeMillis()
        }
    }

    override suspend fun continueConversation(id: String, query: String, response: String, rev: String) {
        update(RealmChatHistory::class.java, "_id", id) { chatHistory ->
            if (chatHistory.conversations == null) {
                chatHistory.conversations = RealmList()
            }

            val conversation = RealmConversation()
            conversation.query = query
            conversation.response = response

            chatHistory.conversations?.add(conversation)
            chatHistory.updatedDate = "${System.currentTimeMillis()}"
            chatHistory.lastUsed = System.currentTimeMillis()
            if (rev.isNotEmpty()) {
                chatHistory._rev = rev
            }
        }
    }
}
