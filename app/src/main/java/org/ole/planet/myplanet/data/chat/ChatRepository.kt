package org.ole.planet.myplanet.data.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.realm.Sort
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmChatHistory

interface ChatRepository {
    suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory>
}

class ChatRepositoryImpl(private val databaseService: DatabaseService) : ChatRepository {
    override suspend fun getChatHistoryForUser(userName: String?): List<RealmChatHistory> {
        return withContext(Dispatchers.IO) {
            databaseService.realmInstance.use { realm ->
                val results = realm.where(RealmChatHistory::class.java)
                    .equalTo("user", userName)
                    .sort("id", Sort.DESCENDING)
                    .findAll()
                realm.copyFromRealm(results)
            }
        }
    }
}
