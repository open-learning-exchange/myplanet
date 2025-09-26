package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.addConversationToChatHistory

class ChatRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
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

    override suspend fun getLatestRevision(chatId: String): String? {
        if (chatId.isEmpty()) {
            return null
        }
        return queryList(RealmChatHistory::class.java) {
            equalTo("_id", chatId)
        }.maxByOrNull { rev ->
            rev._rev?.substringBefore("-")?.toIntOrNull() ?: 0
        }?._rev
    }

    override suspend fun insertChatHistory(chatHistory: JsonObject?) {
        if (chatHistory == null) {
            return
        }
        executeTransaction { realm ->
            RealmChatHistory.insert(realm, chatHistory)
        }
    }

    override suspend fun appendConversationToHistory(
        chatId: String,
        query: String?,
        response: String?,
        newRev: String?,
    ) {
        if (chatId.isEmpty()) {
            return
        }
        executeTransaction { realm ->
            addConversationToChatHistory(realm, chatId, query, response, newRev)
        }
    }
}
