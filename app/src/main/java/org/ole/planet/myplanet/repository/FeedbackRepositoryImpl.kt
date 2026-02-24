package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.Sort
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.JsonUtils

class FeedbackRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson
) : RealmRepository(databaseService), FeedbackRepository {

    override fun createFeedback(
        user: String?,
        urgent: String,
        type: String,
        message: String,
        item: String?,
        state: String?,
    ): RealmFeedback {
        val feedback = RealmFeedback()
        feedback.id = UUID.randomUUID().toString()
        if (state != null) {
            feedback.title = "Question regarding /$state"
            feedback.url = "/$state"
            feedback.state = state
            feedback.item = item
        } else {
            feedback.title = "Question regarding /"
            feedback.url = "/"
        }
        val timestamp = Date().time
        feedback.openTime = timestamp
        feedback.owner = user
        feedback.source = user
        feedback.status = "Open"
        feedback.priority = urgent
        feedback.type = type
        feedback.parentCode = "dev"
        val obj = JsonObject().apply {
            addProperty("message", message)
            addProperty("time", timestamp.toString())
            addProperty("user", user.orEmpty())
        }
        val msgArray = JsonArray().apply { add(obj) }
        feedback.setMessages(msgArray)
        return feedback
    }

    override suspend fun getFeedback(userModel: RealmUser?): Flow<List<RealmFeedback>> =
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

    override suspend fun insertFromJson(jsonObject: JsonObject) {
        executeTransaction { realm ->
            insertFeedbackToRealm(realm, jsonObject)
        }
    }

    override suspend fun insertFeedbackList(jsonObjects: List<JsonObject>) {
        executeTransaction { realm ->
            jsonObjects.forEach { jsonObject ->
                insertFeedbackToRealm(realm, jsonObject)
            }
        }
    }

    private fun insertFeedbackToRealm(mRealm: Realm, act: JsonObject) {
        var feedback = mRealm.where(RealmFeedback::class.java)
            .equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
        if (feedback == null) {
            feedback = mRealm.createObject(RealmFeedback::class.java, JsonUtils.getString("_id", act))
        }
        feedback?._id = JsonUtils.getString("_id", act)
        feedback?.title = JsonUtils.getString("title", act)
        feedback?.source = JsonUtils.getString("source", act)
        feedback?.status = JsonUtils.getString("status", act)
        feedback?.priority = JsonUtils.getString("priority", act)
        feedback?.owner = JsonUtils.getString("owner", act)
        feedback?.openTime = JsonUtils.getLong("openTime", act)
        feedback?.type = JsonUtils.getString("type", act)
        feedback?.url = JsonUtils.getString("url", act)
        feedback?.parentCode = JsonUtils.getString("parentCode", act)
        feedback?.setMessages(JsonUtils.gson.toJson(JsonUtils.getJsonArray("messages", act)))
        feedback?.isUploaded = true
        feedback?.item = JsonUtils.getString("item", act)
        feedback?.state = JsonUtils.getString("state", act)
        feedback?._rev = JsonUtils.getString("_rev", act)
    }
}
