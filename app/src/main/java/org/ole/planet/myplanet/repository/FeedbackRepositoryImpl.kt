package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Sort
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel

class FeedbackRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson
) : RealmRepository(databaseService), FeedbackRepository {

    override suspend fun submitFeedback(
        user: String?,
        urgent: String,
        type: String,
        message: String,
        item: String?,
        state: String?,
    ) {
        val feedback = RealmFeedback().apply {
            id = UUID.randomUUID().toString()
            if (state != null) {
                title = "Question regarding /$state"
                url = "/$state"
                this.state = state
                this.item = item
            } else {
                title = "Question regarding /"
                url = "/"
            }
            openTime = Date().time
            owner = user
            source = user
            status = "Open"
            priority = urgent
            this.type = type
            parentCode = "dev"
            val obj = JsonObject().apply {
                addProperty("message", message)
                addProperty("time", Date().time.toString())
                addProperty("user", user + "")
            }
            val msgArray = JsonArray().apply { add(obj) }
            setMessages(msgArray)
        }
        save(feedback)
    }

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

}
