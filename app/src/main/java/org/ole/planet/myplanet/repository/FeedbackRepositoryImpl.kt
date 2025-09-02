package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Sort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel

class FeedbackRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson,
) : RealmRepository(databaseService), FeedbackRepository {

    override fun getFeedback(userModel: RealmUserModel?): Flow<List<RealmFeedback>> {
        val isManager = userModel?.isManager() == true
        val owner = userModel?.name
        return callbackFlow {
            val feedback = withRealm { realm ->
                if (isManager) {
                    realm.queryList(RealmFeedback::class.java) {
                        sort("openTime", Sort.DESCENDING)
                    }
                } else {
                    realm.queryList(RealmFeedback::class.java) {
                        equalTo("owner", owner)
                        sort("openTime", Sort.DESCENDING)
                    }
                }
            }
            trySend(feedback)
            close()
            awaitClose { }
        }
    }

    override suspend fun getFeedbackById(id: String?): RealmFeedback? {
        return id?.let { findByField(RealmFeedback::class.java, "id", it) }
    }

    override suspend fun closeFeedback(id: String?) {
        id?.let {
            update(RealmFeedback::class.java, "id", it) { feedback ->
                feedback.status = "Closed"
            }
        }
    }

    override suspend fun addReply(id: String?, obj: JsonObject) {
        id?.let {
            update(RealmFeedback::class.java, "id", it) { feedback ->
                val msgArray = gson.fromJson(feedback.messages, JsonArray::class.java)
                msgArray.add(obj)
                feedback.setMessages(msgArray)
            }
        }
    }
}
