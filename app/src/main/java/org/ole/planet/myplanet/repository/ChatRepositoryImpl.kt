package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.addConversationToChatHistory

class ChatRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), ChatRepository {

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
}
