package org.ole.planet.myplanet.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.data.room.dao.FeedbackDao
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.distinctByContent

@Singleton
class FeedbackRepositoryImpl @Inject constructor(
    private val feedbackDao: FeedbackDao, private val timeProvider: TimeProvider
) : FeedbackRepository, FeedbackSyncWriter {

    override suspend fun createAndSaveFeedback(
        user: String?, urgent: String, type: String, message: String, item: String?,
        state: String?
    ) {
        val feedback = createFeedback(user, urgent, type, message, item, state)
        saveFeedback(feedback)
    }

    fun createFeedback(
        user: String?, urgent: String, type: String, message: String, item: String?, state: String?
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
        val timestamp = timeProvider.now()
        feedback.openTime = timestamp
        feedback.owner = user
        feedback.source = user
        feedback.status = "Open"
        feedback.priority = urgent
        feedback.type = type
        feedback.parentCode = "dev"
        val obj = buildJsonObject {
            put("message", message)
            put("time", timestamp.toString())
            put("user", user.orEmpty())
        }
        feedback.setMessages(JsonArray(listOf(obj)))
        return feedback
    }

    override fun getFeedback(ownerName: String?, isManager: Boolean): Flow<List<Feedback>> {
        val flow = if (isManager) {
            feedbackDao.getAllSortedFlow()
        } else {
            feedbackDao.getByOwnerFlow(ownerName)
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
            val obj = buildJsonObject {
                put("message", message)
                put("time", timeProvider.now().toString())
                put("user", user ?: "")
            }
            val messages = feedback.messages
            val msgArray = if (messages.isNullOrEmpty()) {
                JsonArray(emptyList())
            } else {
                KJson.parseArrayOrNull(messages) ?: throw NullPointerException("blank feedback messages")
            }
            feedback.setMessages(JsonArray(msgArray + obj))
            feedback.isUploaded = false
            feedbackDao.update(feedback)
        }
    }

    private suspend fun saveFeedback(feedback: Feedback) {
        feedbackDao.upsert(feedback)
    }

    suspend fun insertFromJson(jsonObject: JsonObject) {
        val id = KJson.getString("_id", jsonObject)
        val existing = feedbackDao.findById(id)
        feedbackDao.upsert(mapToFeedback(jsonObject, existing, id))
    }

    override suspend fun insertFeedbackList(jsonObjects: List<JsonObject>) {
        val ids = jsonObjects.map { KJson.getString("_id", it) }
        val existingById = feedbackDao.getByIds(ids).associateBy { it.id }
        feedbackDao.upsertAll(
            jsonObjects.zip(ids) { json, id -> mapToFeedback(json, existingById[id], id) }
        )
    }

    override suspend fun markFeedbackUploaded(id: String, remoteId: String, remoteRev: String): Boolean {
        return feedbackDao.markUploaded(id, remoteId, remoteRev) > 0
    }

    private fun mapToFeedback(act: JsonObject, existing: Feedback?, idStr: String): Feedback {
        val hasPendingLocalChange = existing?.isUploaded == false
        return Feedback().apply {
            id = idStr
            _id = idStr
            title = KJson.getString("title", act)
            source = KJson.getString("source", act)
            status = if (hasPendingLocalChange && existing.status == "Closed") existing.status else KJson.getString("status", act)
            priority = KJson.getString("priority", act)
            owner = KJson.getString("owner", act)
            openTime = KJson.getLong("openTime", act)
            type = KJson.getString("type", act)
            url = KJson.getString("url", act)
            parentCode = KJson.getString("parentCode", act)
            item = KJson.getString("item", act)
            state = KJson.getString("state", act)
            _rev = KJson.getString("_rev", act)
            if (hasPendingLocalChange) {
                messages = existing.messages
                isUploaded = false
            } else {
                messages = KJson.gsonToJson(KJson.getJsonArray("messages", act))
                isUploaded = true
            }
        }
    }
}
