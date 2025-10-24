package org.ole.planet.myplanet.repository

import io.realm.Case
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.addConversationToChatHistory
import org.ole.planet.myplanet.model.RealmNews

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

    override suspend fun getPlanetNewsMessages(planetCode: String?): List<RealmNews> {
        if (planetCode.isNullOrEmpty()) {
            return emptyList()
        }
        return queryList(RealmNews::class.java) {
            equalTo("docType", "message", Case.INSENSITIVE)
            equalTo("createdOn", planetCode, Case.INSENSITIVE)
        }
    }

    override suspend fun appendConversationToChatHistory(
        chatHistoryId: String?,
        query: String?,
        response: String?,
        newRev: String?,
    ) {
        executeTransaction { realm ->
            addConversationToChatHistory(realm, chatHistoryId, query, response, newRev)
        }
    }

    override suspend fun getLatestRevisionId(chatHistoryId: String): String? {
        if (chatHistoryId.isBlank()) {
            return null
        }
        return withRealmAsync { realm ->
            realm.refresh()
            realm.where(RealmChatHistory::class.java)
                .equalTo("_id", chatHistoryId)
                .findAll()
                .maxByOrNull { rev -> parseRevisionNumber(rev._rev) }
                ?._rev
        }
    }

    private fun parseRevisionNumber(revision: String?): Int {
        return revision?.substringBefore('-')?.toIntOrNull() ?: 0
    }
}
