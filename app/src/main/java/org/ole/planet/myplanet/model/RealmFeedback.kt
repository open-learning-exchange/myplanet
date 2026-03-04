package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.io.StringReader
import org.ole.planet.myplanet.utils.JsonUtils

open class RealmFeedback : RealmObject() {
    @PrimaryKey
    var id: String? = null
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
        private set
    var item: String? = null
    var parentCode: String? = null
    var state: String? = null
    fun setMessages(messages: JsonArray?) {
        this.messages = JsonUtils.gson.toJson(messages)
    }

    val messageList: List<FeedbackReply>?
        get() {
            if (TextUtils.isEmpty(messages)) return null
            val feedbackReplies: MutableList<FeedbackReply> = ArrayList()

            val stringReader = StringReader(messages)
            val jsonReader = JsonReader(stringReader)

            val e = JsonParser.parseReader(jsonReader)
            val ar = e.asJsonArray
            if (ar.size() > 0) {
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
            }
            return feedbackReplies
        }

    val message: String
        get() {
            if (TextUtils.isEmpty(messages)) return ""

            val stringReader = StringReader(messages)
            val jsonReader = JsonReader(stringReader)

            val e = JsonParser.parseReader(jsonReader)
            val ar = e.asJsonArray
            if (ar.size() > 0) {
                val ob = ar[0].asJsonObject
                return ob["message"].asString
            }
            return ""
        }

    fun setMessages(messages: String?) {
        this.messages = messages
    }

    companion object {
        @JvmStatic
        fun serializeFeedback(feedback: RealmFeedback): JsonObject {
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
