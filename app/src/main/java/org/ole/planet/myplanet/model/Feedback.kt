package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

@Entity(tableName = "feedback", indices = [androidx.room.Index("openTime"), androidx.room.Index("owner"), androidx.room.Index("isUploaded")])
open class Feedback {
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
        set(value) {
            field = value
            cachedMessages = null
        }
    var item: String? = null
    var parentCode: String? = null
    var state: String? = null

    @Ignore
    @Transient
    private var cachedMessages: JsonArray? = null

    private fun parsedMessages(): JsonArray {
        if (messages.isNullOrEmpty()) return JsonArray()
        cachedMessages?.let { return it }
        val ar = JsonParser.parseString(messages).asJsonArray
        cachedMessages = ar
        return ar
    }

    fun setMessages(messages: JsonArray?) {
        this.messages = JsonUtils.gson.toJson(messages)
    }

    @get:Ignore
    val messageList: List<FeedbackReply>?
        get() {
            if (messages.isNullOrEmpty()) return null
            val feedbackReplies: MutableList<FeedbackReply> = ArrayList()

            val ar = parsedMessages()
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

            val ar = parsedMessages()
            if (!ar.isEmpty()) {
                val ob = ar[0].asJsonObject
                return ob["message"].asString
            }
            return ""
        }

    companion object {
        fun serializeFeedback(feedback: Feedback): JsonObject {
            val messagesJson = try {
                JsonParser.parseString(feedback.messages)
            } catch (err: Exception) {
                err.printStackTrace()
                null
            }
            val `object` = buildJsonObject {
                put("title", feedback.title)
                put("source", feedback.source)
                put("status", feedback.status)
                put("priority", feedback.priority)
                put("owner", feedback.owner)
                put("openTime", feedback.openTime)
                put("type", feedback.type)
                put("url", feedback.url)
                put("parentCode", feedback.parentCode)
                put("state", feedback.state)
                put("item", feedback.item)
                if (feedback._id != null) put("_id", feedback._id)
                if (feedback._rev != null) put("_rev", feedback._rev)
                if (messagesJson != null) put("messages", messagesJson.toKotlinx())
            }.toGson()
            `object`.addDocumentOrigin()
            return `object`
        }
    }
}
