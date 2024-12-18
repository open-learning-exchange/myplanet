package org.ole.planet.myplanet.model

import com.google.gson.*
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import java.io.File
import java.io.FileWriter
import java.io.IOException
import kotlin.collections.MutableList

class RealmFeedback : RealmObject {
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
    private var messages: String? = null
    var item: String? = null
    var parentCode: String? = null
    var state: String? = null

    fun setMessages(messages: JsonArray?) {
        this.messages = Gson().toJson(messages)
    }

    val messageList: List<FeedbackReply>?
        get() = if (messages.isNullOrEmpty()) {
            null
        } else {
            try {
                val jsonArray = JsonParser.parseString(messages).asJsonArray
                jsonArray.map {
                    val jsonObject = it.asJsonObject
                    FeedbackReply(
                        jsonObject["message"].asString,
                        jsonObject["user"].asString,
                        jsonObject["time"].asString
                    )
                }
            } catch (e: Exception) {
                null
            }
        }

    val message: String
        get() = if (messages.isNullOrEmpty()) {
            ""
        } else {
            try {
                val jsonArray = JsonParser.parseString(messages).asJsonArray
                if (jsonArray.size() > 0) {
                    jsonArray[0].asJsonObject["message"].asString
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }

    companion object {
        private val feedbacksDataList: MutableList<Array<String>> = mutableListOf()

        suspend fun insert(realm: Realm, act: JsonObject) {
            realm.writeBlocking {
                val feedback = query<RealmFeedback>("_id == $0", act["_id"].asString).first().find()
                    ?: RealmFeedback().apply {
                        _id = act["_id"].asString
                    }.also { copyToRealm(it) }

                feedback.apply {
                    title = act["title"].asString
                    source = act["source"].asString
                    status = act["status"].asString
                    priority = act["priority"].asString
                    owner = act["owner"].asString
                    openTime = act["openTime"].asLong
                    type = act["type"].asString
                    url = act["url"].asString
                    parentCode = act["parentCode"].asString
                    setMessages(act["messages"].asJsonArray)
                    isUploaded = true
                    item = act["item"].asString
                    state = act["state"].asString
                    _rev = act["_rev"].asString
                }

                feedbacksDataList.add(
                    arrayOf(
                        act["_id"].asString,
                        act["title"].asString,
                        act["source"].asString,
                        act["status"].asString,
                        act["priority"].asString,
                        act["owner"].asString,
                        act["openTime"].asString,
                        act["type"].asString,
                        act["url"].asString,
                        act["parentCode"].asString,
                        act["state"].asString,
                        act["item"].asString,
                        act["messages"].asJsonArray.toString()
                    )
                )
            }
        }

        fun serializeFeedback(feedback: RealmFeedback): JsonObject {
            val jsonObject = JsonObject()
            jsonObject.addProperty("title", feedback.title)
            jsonObject.addProperty("source", feedback.source)
            jsonObject.addProperty("status", feedback.status)
            jsonObject.addProperty("priority", feedback.priority)
            jsonObject.addProperty("owner", feedback.owner)
            jsonObject.addProperty("openTime", feedback.openTime)
            jsonObject.addProperty("type", feedback.type)
            jsonObject.addProperty("url", feedback.url)
            jsonObject.addProperty("parentCode", feedback.parentCode)
            jsonObject.addProperty("state", feedback.state)
            jsonObject.addProperty("item", feedback.item)
            feedback._id?.let { jsonObject.addProperty("_id", it) }
            feedback._rev?.let { jsonObject.addProperty("_rev", it) }

            try {
                feedback.messages?.let {
                    jsonObject.add("messages", JsonParser.parseString(it))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return jsonObject
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf(
                        "feedbackId","title","source","status","priority","owner","openTime","type","url","parentCode","state","item","messages"
                    ))
                    data.forEach { row ->
                        writer.writeNext(row)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun feedbackWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/feedback.csv", feedbacksDataList)
        }
    }
}