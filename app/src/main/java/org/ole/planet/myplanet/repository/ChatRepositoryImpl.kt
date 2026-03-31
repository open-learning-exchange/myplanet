package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.realm.Sort
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.addConversationToChatHistory

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
            RealmChatHistory.insert(realm, chat)
        }
    }

    override suspend fun continueConversation(id: String, query: String, response: String, rev: String) {
        executeTransaction { realm ->
            addConversationToChatHistory(realm, id, query, response, rev)
        }
    }

    override fun insertChatHistoryBatch(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val chatHistoryList = mutableListOf<JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            chatHistoryList.add(jsonDoc)
        }
        chatHistoryList.forEach { jsonDoc ->
            org.ole.planet.myplanet.model.RealmChatHistory.insert(realm, jsonDoc)
        }
    }
}
