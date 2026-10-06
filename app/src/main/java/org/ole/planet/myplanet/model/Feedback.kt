package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.KJson.GsonNumbers
import org.ole.planet.myplanet.utils.addDocumentOrigin

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
            cachedMessageList = null
        }
    var item: String? = null
    var parentCode: String? = null
    var state: String? = null

    @Ignore
    @Transient
    private var cachedMessages: JsonArray? = null

    @Ignore
    @Transient
    private var cachedMessageList: List<FeedbackReply>? = null

    private fun parsedMessages(): JsonArray {
        if (messages.isNullOrEmpty()) return JsonArray(emptyList())
        cachedMessages?.let { return it }
        val ar = parseMessages(messages) as? JsonArray ?: throw IllegalStateException("Not a JSON Array: $messages")
        cachedMessages = ar
        return ar
    }

    /** Stored exactly as Gson wrote it; [messages] mirrors the caller's Gson tree, so numbers print as written. */
    fun setMessages(messages: JsonArray?) {
        this.messages = KJson.gsonToJson(messages, GsonNumbers.LITERAL)
    }

    @get:Ignore
    val messageList: List<FeedbackReply>?
        get() {
            if (messages.isNullOrEmpty()) return null
            cachedMessageList?.let { return it }
            val feedbackReplies: MutableList<FeedbackReply> = ArrayList()

            val ar = parsedMessages()
            for (i in 1 until ar.size) {
                val ob = KJson.asJsonObject(ar[i])
                feedbackReplies.add(
                    FeedbackReply(
                        KJson.gsonAsString(ob["message"], GsonNumbers.LITERAL),
                        KJson.gsonAsString(ob["user"], GsonNumbers.LITERAL),
                        KJson.gsonAsString(ob["time"], GsonNumbers.LITERAL)
                    )
                )
            }
            cachedMessageList = feedbackReplies
            return feedbackReplies
        }

    @get:Ignore
    val message: String
        get() {
            if (messages.isNullOrEmpty()) return ""

            val ar = parsedMessages()
            if (ar.isNotEmpty()) {
                val ob = KJson.asJsonObject(ar[0])
                return KJson.gsonAsString(ob["message"], GsonNumbers.LITERAL)
            }
            return ""
        }

    companion object {
        /** Gson's `JsonParser.parseString`: blank text reads as JSON `null`, and a `null` string throws. */
        private fun parseMessages(text: String?): JsonElement {
            if (text == null) throw NullPointerException("messages is null")
            return KJson.parseOrNull(text) ?: JsonNull
        }

        fun serializeFeedback(feedback: Feedback): JsonObject {
            val messagesJson = try {
                parseMessages(feedback.messages)
            } catch (err: Exception) {
                err.printStackTrace()
                null
            }
            return buildJsonObject {
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
                if (messagesJson != null) put("messages", messagesJson)
                addDocumentOrigin()
            }
        }
    }
}
