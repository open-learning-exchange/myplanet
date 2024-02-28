package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.io.StringReader

open class RealmFeedback : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    private var _id: String? = null
    @JvmField
    var title: String? = null
    @JvmField
    var source: String? = null
    @JvmField
    var status: String? = null
    @JvmField
    var priority: String? = null
    @JvmField
    var owner: String? = null
    @JvmField
    var openTime: String? = null
    @JvmField
    var type: String? = null
    @JvmField
    var url: String? = null
    @JvmField
    var isUploaded = false
    private var _rev: String? = null
    var messages: String? = null
        private set
    @JvmField
    var item: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var state: String? = null
    fun setMessages(messages: JsonArray?) {
        this.messages = Gson().toJson(messages)
    }

    fun get_rev(): String? {
        return _rev
    }

    fun set_rev(_rev: String?) {
        this._rev = _rev
    }

    fun get_id(): String? {
        return _id
    }

    fun set_id(_id: String?) {
        this._id = _id
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
            if (feedback.get_id() != null) `object`.addProperty("_id", feedback.get_id())
            if (feedback.get_rev() != null) `object`.addProperty("_rev", feedback.get_rev())

            try {
                `object`.add("messages", JsonParser.parseString(feedback.messages))
            } catch (err: Exception) {
                err.printStackTrace()
            }
            Utilities.log("OBJECT " + Gson().toJson(`object`))
            return `object`
        }


        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            mRealm.executeTransactionAsync { realm ->
                var feedback = realm.where(RealmFeedback::class.java)
                    .equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
                if (feedback == null) feedback =
                    realm.createObject(RealmFeedback::class.java, JsonUtils.getString("_id", act))
                feedback!!.set_id(JsonUtils.getString("_id", act))
                feedback.title = JsonUtils.getString("title", act)
                feedback.source = JsonUtils.getString("source", act)
                feedback.status = JsonUtils.getString("status", act)
                feedback.priority = JsonUtils.getString("priority", act)
                feedback.owner = JsonUtils.getString("owner", act)
                feedback.openTime = JsonUtils.getString("openTime", act)
                feedback.type = JsonUtils.getString("type", act)
                feedback.url = JsonUtils.getString("url", act)
                feedback.parentCode = JsonUtils.getString("parentCode", act)
                feedback.setMessages(Gson().toJson(JsonUtils.getJsonArray("messages", act)))
                feedback.isUploaded = true
                feedback.item = JsonUtils.getString("item", act)
                feedback.state = JsonUtils.getString("state", act)
                feedback.set_rev(JsonUtils.getString("_rev", act))
            }
        }
    }
}