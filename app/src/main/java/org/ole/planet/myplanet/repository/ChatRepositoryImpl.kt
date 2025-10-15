package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.realm.Case
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmChatHistory
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

    override suspend fun getLatestRevision(chatId: String): String? {
        if (chatId.isBlank()) {
            return null
        }
        return withRealmAsync { realm ->
            realm.refresh()
            val results = realm.where(RealmChatHistory::class.java)
                .equalTo("_id", chatId)
                .findAll()
            results.maxByOrNull { history ->
                history._rev?.substringBefore("-")?.toIntOrNull() ?: 0
            }?._rev
        }
    }

    override suspend fun saveChatHistory(payload: JsonObject) {
        executeTransaction { realm ->
            RealmChatHistory.insert(realm, payload)
        }
    }

    override suspend fun appendConversation(chatId: String, query: String, response: String, newRev: String?) {
        if (chatId.isBlank()) {
            return
        }
        withRealmAsync { realm ->
            try {
                RealmChatHistory.addConversationToChatHistory(realm, chatId, query, response, newRev)
                if (realm.isInTransaction) {
                    realm.commitTransaction()
                }
            } catch (exception: Exception) {
                if (realm.isInTransaction) {
                    realm.cancelTransaction()
                }
                exception.printStackTrace()
            }
        }
    }
}
