package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.progressDataList
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.StringReader

open class RealmFeedback : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _id: String? = null
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
    var openTime: Long = 0
    @JvmField
    var type: String? = null
    @JvmField
    var url: String? = null
    @JvmField
    var isUploaded = false
    @JvmField
    var _rev: String? = null
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
        val feedbacksDataList: MutableList<Array<String>> = mutableListOf()
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

        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            var feedback = mRealm.where(RealmFeedback::class.java).equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
            if (feedback == null) {
                feedback = mRealm.createObject(RealmFeedback::class.java, JsonUtils.getString("_id", act))
            }
            feedback?._id = JsonUtils.getString("_id", act)
            feedback?.title = JsonUtils.getString("title", act)
            feedback?.source = JsonUtils.getString("source", act)
            feedback?.status = JsonUtils.getString("status", act)
            feedback?.priority = JsonUtils.getString("priority", act)
            feedback?.owner = JsonUtils.getString("owner", act)
            feedback?.openTime = JsonUtils.getLong("openTime", act)
            feedback?.type = JsonUtils.getString("type", act)
            feedback?.url = JsonUtils.getString("url", act)
            feedback?.parentCode = JsonUtils.getString("parentCode", act)
            feedback?.setMessages(Gson().toJson(JsonUtils.getJsonArray("messages", act)))
            feedback?.isUploaded = true
            feedback?.item = JsonUtils.getString("item", act)
            feedback?.state = JsonUtils.getString("state", act)
            feedback?._rev = JsonUtils.getString("_rev", act)
            mRealm.commitTransaction()

            val csvRow = arrayOf(
                JsonUtils.getString("_id", act),
                JsonUtils.getString("title", act),
                JsonUtils.getString("source", act),
                JsonUtils.getString("status", act),
                JsonUtils.getString("priority", act),
                JsonUtils.getString("owner", act),
                JsonUtils.getLong("openTime", act).toString(),
                JsonUtils.getString("type", act),
                JsonUtils.getString("url", act),
                JsonUtils.getString("parentCode", act),
                JsonUtils.getString("state", act),
                JsonUtils.getString("item", act),
                JsonUtils.getJsonArray("messages", act).toString()
            )
            feedbacksDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("feedbackId", "title", "source", "status", "priority", "owner", "openTime", "type", "url", "parentCode", "state", "item", "messages"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun feedbackWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/feedback.csv", feedbacksDataList)
        }
    }
}
