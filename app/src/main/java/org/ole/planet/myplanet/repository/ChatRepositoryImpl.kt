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
        save(RealmChatHistory.fromJson(chat))
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
