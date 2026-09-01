package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.room.dao.FeedbackDao
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.distinctByContent

@Singleton
class FeedbackRepositoryImpl @Inject constructor(
    private val feedbackDao: FeedbackDao,
    private val gson: Gson
) : FeedbackRepository, FeedbackSyncWriter {

    override suspend fun createAndSaveFeedback(
        user: String?,
        urgent: String,
        type: String,
        message: String,
        item: String?,
        state: String?,
    ) {
        val feedback = createFeedback(user, urgent, type, message, item, state)
        saveFeedback(feedback)
    }

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

    override suspend fun getFeedback(userModel: UserEntity?): Flow<List<Feedback>> {
        val flow = if (userModel?.isManager() == true) {
            feedbackDao.getAllSortedFlow()
        } else {
            feedbackDao.getByOwnerFlow(userModel?.name)
        }
        return flow.distinctByContent { a, b ->
            // Compare CouchDB sync markers alongside local status changes and reply messages
            a.id == b.id && a._rev == b._rev && a.status == b.status &&
                a.isUploaded == b.isUploaded && a.messages == b.messages
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
            val messages = feedback.messages
            val msgArray = if (messages.isNullOrEmpty()) JsonArray() else gson.fromJson(messages, JsonArray::class.java)
            msgArray.add(obj)
            feedback.setMessages(msgArray)
            feedback.isUploaded = false
            feedbackDao.update(feedback)
        }
    }

    override suspend fun saveFeedback(feedback: Feedback) {
        feedbackDao.upsert(feedback)
    }

    suspend fun insertFromJson(jsonObject: JsonObject) {
        val id = JsonUtils.getString("_id", jsonObject)
        val existing = feedbackDao.findById(id)
        feedbackDao.upsert(mapToFeedback(jsonObject, existing, id))
    }

    override suspend fun insertFeedbackList(jsonObjects: List<JsonObject>) {
        val ids = jsonObjects.map { JsonUtils.getString("_id", it) }
        val existingById = feedbackDao.getByIds(ids).associateBy { it.id }
        feedbackDao.upsertAll(
            jsonObjects.zip(ids) { json, id -> mapToFeedback(json, existingById[id], id) }
        )
    }

    override suspend fun markFeedbackUploaded(id: String): Boolean {
        return feedbackDao.markUploaded(id) > 0
    }

    private fun mapToFeedback(act: JsonObject, existing: Feedback?, idStr: String): Feedback {
        val hasPendingLocalReply = existing?.isUploaded == false
        return Feedback().apply {
            id = idStr
            _id = idStr
            title = JsonUtils.getString("title", act)
            source = JsonUtils.getString("source", act)
            status = JsonUtils.getString("status", act)
            priority = JsonUtils.getString("priority", act)
            owner = JsonUtils.getString("owner", act)
            openTime = JsonUtils.getLong("openTime", act)
            type = JsonUtils.getString("type", act)
            url = JsonUtils.getString("url", act)
            parentCode = JsonUtils.getString("parentCode", act)
            item = JsonUtils.getString("item", act)
            state = JsonUtils.getString("state", act)
            _rev = JsonUtils.getString("_rev", act)
            if (hasPendingLocalReply) {
                messages = existing.messages
                isUploaded = false
            } else {
                messages = JsonUtils.gson.toJson(JsonUtils.getJsonArray("messages", act))
                isUploaded = true
            }
        }
    }
}
