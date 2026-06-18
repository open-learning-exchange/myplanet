package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.Sort
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.JsonUtils

class FeedbackRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val gson: Gson
) : RealmRepository(databaseService, realmDispatcher), FeedbackRepository {

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
        save(mapToRealmFeedback(jsonObject))
    }

    override suspend fun insertFeedbackList(jsonObjects: List<JsonObject>) {
        executeTransaction { realm ->
            realm.copyToRealmOrUpdate(jsonObjects.map { mapToRealmFeedback(it) })
        }
    }

    private fun mapToRealmFeedback(act: JsonObject): RealmFeedback {
        return RealmFeedback().apply {
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
            setMessages(JsonUtils.gson.toJson(JsonUtils.getJsonArray("messages", act)))
            isUploaded = true
            item = JsonUtils.getString("item", act)
            state = JsonUtils.getString("state", act)
            _rev = JsonUtils.getString("_rev", act)
        }
    }

    override fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val documentList = ArrayList<com.google.gson.JsonObject>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            realm.copyToRealmOrUpdate(mapToRealmFeedback(jsonDoc))
        }
    }
}
