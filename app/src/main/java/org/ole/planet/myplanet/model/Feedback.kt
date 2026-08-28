package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.ole.planet.myplanet.utils.JsonUtils

/**
 * Room replacement for the former `Feedback` model. Uploaded (Room upload path) and
 * synced; persistence goes through [org.ole.planet.myplanet.data.room.dao.FeedbackDao]. The replies
 * are stored as a JSON array string in [messages]; the derived [messageList]/[message] views are
 * ignored by Room.
 */
@Entity(tableName = "feedback", indices = [androidx.room.Index("openTime"), androidx.room.Index("owner"), androidx.room.Index("isUploaded")])
open class Feedback {
    // @JvmField on id/_id so Room does not see ambiguous getId/get_id accessors.
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var title: String? = null
    var source: String? = null
    var status: String? = null
    var priority: String? = null
    var owner: String? = null
    var openTime: Long = 0
    var type: String? = null
    var url: String? = null
    var isUploaded = false
    var _rev: String? = null
    var messages: String? = null
    var item: String? = null
    var parentCode: String? = null
    var state: String? = null

    fun setMessages(messages: JsonArray?) {
        this.messages = JsonUtils.gson.toJson(messages)
    }

    @get:Ignore
    val messageList: List<FeedbackReply>?
        get() {
            if (messages.isNullOrEmpty()) return null
            val feedbackReplies: MutableList<FeedbackReply> = ArrayList()

            val ar = JsonParser.parseString(messages).asJsonArray
            for (i in 1 until ar.size()) {
                val ob = ar[i].asJsonObject
                feedbackReplies.add(
                    FeedbackReply(
                        ob["message"].asString,
                        ob["user"].asString,
                        ob["time"].asString
                    )
                )
            }
            return feedbackReplies
        }

    @get:Ignore
    val message: String
        get() {
            if (messages.isNullOrEmpty()) return ""

            val ar = JsonParser.parseString(messages).asJsonArray
            if (!ar.isEmpty()) {
                val ob = ar[0].asJsonObject
                return ob["message"].asString
            }
            return ""
        }

    companion object {
        fun serializeFeedback(feedback: Feedback): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("title", feedback.title)
            `object`.addProperty("source", feedback.source)
            `object`.addProperty("status", feedback.status)
            `object`.addProperty("priority", feedback.priority)
            `object`.addProperty("owner", feedback.owner)
            `object`.addProperty("openTime", feedback.openTime)
            `object`.addProperty("type", feedback.type)
            `object`.addProperty("url", feedback.url)
            `object`.addProperty("parentCode", feedback.parentCode)
            `object`.addProperty("state", feedback.state)
            `object`.addProperty("item", feedback.item)
            if (feedback._id != null) `object`.addProperty("_id", feedback._id)
            if (feedback._rev != null) `object`.addProperty("_rev", feedback._rev)

            try {
                `object`.add("messages", JsonParser.parseString(feedback.messages))
            } catch (err: Exception) {
                err.printStackTrace()
            }
            return `object`
        }
    }
}
