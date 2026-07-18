package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.room.dao.FeedbackDao
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.JsonUtils

class FeedbackRepositoryImpl @Inject constructor(
    private val feedbackDao: FeedbackDao,
    private val gson: Gson
) : FeedbackRepository {

    override fun createFeedback(
        user: String?,
        urgent: String,
        type: String,
        message: String,
        item: String?,
        state: String?,
    ): Feedback {
        val feedback = Feedback()
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

    override suspend fun getFeedback(userModel: RealmUser?): Flow<List<Feedback>> {
        return if (userModel?.isManager() == true) {
            feedbackDao.getAllSortedFlow()
        } else {
            feedbackDao.getByOwnerFlow(userModel?.name)
        }
    }

    override suspend fun getPendingFeedback(): List<Feedback> {
        return feedbackDao.getPending()
    }

    override suspend fun getFeedbackById(id: String?): Feedback? {
        return id?.let { feedbackDao.findById(it) }
    }

    override suspend fun closeFeedback(id: String?) {
        id?.let { feedbackDao.closeById(it) }
    }

    override suspend fun addReply(id: String?, message: String, user: String?) {
        id?.let {
            val feedback = feedbackDao.findById(it) ?: return
            val obj = JsonObject().apply {
                addProperty("message", message)
                addProperty("time", Date().time.toString())
                addProperty("user", user ?: "")
            }
            val msgArray = gson.fromJson(feedback.messages, JsonArray::class.java)
            msgArray.add(obj)
            feedback.setMessages(msgArray)
            feedbackDao.update(feedback)
        }
    }

    override suspend fun saveFeedback(feedback: Feedback) {
        feedbackDao.upsert(feedback)
    }

    override suspend fun insertFromJson(jsonObject: JsonObject) {
        feedbackDao.upsert(mapToFeedback(jsonObject))
    }

    override suspend fun insertFeedbackList(jsonObjects: List<JsonObject>) {
        feedbackDao.upsertAll(jsonObjects.map { mapToFeedback(it) })
    }

    override suspend fun markFeedbackUploaded(id: String): Boolean {
        return feedbackDao.markUploaded(id) > 0
    }

    private fun mapToFeedback(act: JsonObject): Feedback {
        return Feedback().apply {
            id = JsonUtils.getString("_id", act)
            _id = JsonUtils.getString("_id", act)
            title = JsonUtils.getString("title", act)
            source = JsonUtils.getString("source", act)
            status = JsonUtils.getString("status", act)
            priority = JsonUtils.getString("priority", act)
            owner = JsonUtils.getString("owner", act)
            openTime = JsonUtils.getLong("openTime", act)
            type = JsonUtils.getString("type", act)
            url = JsonUtils.getString("url", act)
            parentCode = JsonUtils.getString("parentCode", act)
            messages = JsonUtils.gson.toJson(JsonUtils.getJsonArray("messages", act))
            isUploaded = true
            item = JsonUtils.getString("item", act)
            state = JsonUtils.getString("state", act)
            _rev = JsonUtils.getString("_rev", act)
        }
    }
}
