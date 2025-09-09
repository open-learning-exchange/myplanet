package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Sort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel

class FeedbackRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson
) : RealmRepository(databaseService), FeedbackRepository {

    override fun getFeedback(userModel: RealmUserModel?): Flow<List<RealmFeedback>> =
        queryListFlow(RealmFeedback::class.java) {
            if (userModel?.isManager() == true) {
                sort("openTime", Sort.DESCENDING)
            } else {
                equalTo("owner", userModel?.name)
                sort("openTime", Sort.DESCENDING)
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

    override suspend fun saveFeedback(feedback: RealmFeedback) {
        save(feedback)
    }
}
