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

    override suspend fun getChatHistoryForUser(ownerId: String?, userName: String?): List<RealmChatHistory> {
        if (ownerId.isNullOrEmpty() && userName.isNullOrEmpty()) {
            return emptyList()
        }
        return queryList(RealmChatHistory::class.java) {
            when {
                !ownerId.isNullOrEmpty() -> {
                    beginGroup()
                    equalTo("ownerId", ownerId)
                    if (!userName.isNullOrEmpty()) {
                        or()
                        equalTo("user", userName)
                    }
                    endGroup()
                }
                !userName.isNullOrEmpty() -> {
                    equalTo("user", userName)
                }
            }
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
}
