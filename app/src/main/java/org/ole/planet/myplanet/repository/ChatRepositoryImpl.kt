package org.ole.planet.myplanet.repository

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
        if (chatId.isEmpty()) {
            return null
        }
        return withRealmAsync { realm ->
            realm.refresh()
            realm.where(RealmChatHistory::class.java)
                .equalTo("_id", chatId)
                .findAll()
                .maxByOrNull { rev -> rev._rev?.split("-")?.get(0)?.toIntOrNull() ?: 0 }
                ?._rev
        }
    }
}
