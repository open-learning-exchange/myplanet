package org.ole.planet.myplanet.data

import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.addConversationToChatHistory
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.insert
import javax.inject.Inject

interface ChatRepository {
    fun getChatHistory(user: String): Flow<List<RealmChatHistory>>
    suspend fun getLatestRev(id: String): String?
    suspend fun addConversation(chatHistoryId: String?, query: String?, response: String?, newRev: String?)
    suspend fun insertChatHistory(jsonObject: JsonObject)
    fun getRealm(): Realm
}

class ChatRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : ChatRepository {

    override fun getChatHistory(user: String): Flow<List<RealmChatHistory>> = flow {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.use { realm ->
                val results = realm.where(RealmChatHistory::class.java)
                    .equalTo("user", user)
                    .sort("id", Sort.DESCENDING)
                    .findAll()
                emit(realm.copyFromRealm(results))
            }
        }
    }

    override suspend fun getLatestRev(id: String): String? = withContext(Dispatchers.IO) {
        databaseService.realmInstance.use { realm ->
            val chat = realm.where(RealmChatHistory::class.java)
                .equalTo("_id", id)
                .findAll()
                .maxByOrNull { rev -> rev._rev?.split("-")?.getOrNull(0)?.toIntOrNull() ?: 0 }
            chat?._rev
        }
    }

    override suspend fun addConversation(chatHistoryId: String?, query: String?, response: String?, newRev: String?) {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.use { realm ->
                if (!realm.isInTransaction) realm.beginTransaction()
                try {
                    addConversationToChatHistory(realm, chatHistoryId, query, response, newRev)
                    realm.commitTransaction()
                } catch (e: Exception) {
                    if (realm.isInTransaction) realm.cancelTransaction()
                    throw e
                }
            }
        }
    }

    override suspend fun insertChatHistory(jsonObject: JsonObject) {
        withContext(Dispatchers.IO) {
            databaseService.realmInstance.use { realm ->
                realm.executeTransaction { r ->
                    insert(r, jsonObject)
                }
            }
        }
    }

    override fun getRealm(): Realm {
        return databaseService.realmInstance
    }
}
