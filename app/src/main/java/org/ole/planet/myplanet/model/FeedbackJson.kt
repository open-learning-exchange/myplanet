package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

// App-side Gson helpers for Feedback, kept out of the Room entity.

private fun Feedback.parsedMessages(): JsonArray {
    if (messages.isNullOrEmpty()) return JsonArray()
    (cachedMessages as JsonArray?)?.let { return it }
    val ar = JsonParser.parseString(messages).asJsonArray
    cachedMessages = ar
    return ar
}

fun Feedback.setMessages(messages: JsonArray?) {
    this.messages = GsonUtils.gson.toJson(messages)
}

val Feedback.messageList: List<FeedbackReply>?
    get() {
        if (messages.isNullOrEmpty()) return null
        cachedMessageList?.let { return it }
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
        cachedMessageList = feedbackReplies
        return feedbackReplies
    }

val Feedback.message: String
    get() {
        if (messages.isNullOrEmpty()) return ""

        val ar = parsedMessages()
        if (!ar.isEmpty()) {
            val ob = ar[0].asJsonObject
            return ob["message"].asString
        }
        return ""
    }

fun Feedback.Companion.serializeFeedback(feedback: Feedback): JsonObject {
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
