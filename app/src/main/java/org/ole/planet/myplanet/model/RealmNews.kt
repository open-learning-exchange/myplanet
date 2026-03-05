package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.PrimaryKey
import java.util.Date
import java.util.UUID
import org.ole.planet.myplanet.utils.JsonUtils

open class RealmNews : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var userId: String? = null
    var user: String? = null
    var message: String? = null
    var docType: String? = null
    var viewableBy: String? = null
    var viewableId: String? = null
    var avatar: String? = null
    var replyTo: String? = null
    var userName: String? = null
    var messagePlanetCode: String? = null
    var messageType: String? = null
    var updatedDate: Long = 0
    var time: Long = 0
    var createdOn: String? = null
    var parentCode: String? = null
    var imageUrls: RealmList<String>? = null
    var images: String? = null
    var labels: RealmList<String>? = null
    var viewIn: String? = null
    var newsId: String? = null
    var newsRev: String? = null
    var newsUser: String? = null
    var aiProvider: String? = null
    var newsTitle: String? = null
    var conversations: String? = null
    var newsCreatedDate: Long = 0
    var newsUpdatedDate: Long = 0
    var chat: Boolean = false
    var isEdited: Boolean = false
    var editedTime: Long = 0
    var sharedBy: String? = null
    @Ignore
    var sortDate: Long = 0

    val imagesArray: JsonArray
        get() = if (images == null) JsonArray() else JsonUtils.gson.fromJson(images, JsonArray::class.java)

    val labelsArray: JsonArray
        get() {
            val array = JsonArray()
            labels?.forEach { s ->
                array.add(s)
            }
            return array
        }

    fun updateMessage(newMessage: String) {
        this.message = newMessage
        this.isEdited = true
        this.editedTime = Date().time
    }
    fun setLabels(images: JsonArray) {
        labels = RealmList()
        for (ob in images) {
            labels?.add(ob.asString)
        }
    }

    val messageWithoutMarkdown: String?
        get() {
            var ms = message
            for (ob in imagesArray) {
                ms = ms?.replace(JsonUtils.getString("markdown", ob.asJsonObject), "")
            }
            return ms
        }

    val isCommunityNews: Boolean
        get() {
            val array = JsonUtils.gson.fromJson(viewIn, JsonArray::class.java)
            var isCommunity = false
            for (e in array) {
                val `object` = e.asJsonObject
                if (`object`.has("section") && `object`["section"].asString.equals("community", ignoreCase = true)) {
                    isCommunity = true
                    break
                }
            }
            return isCommunity
        }

    fun calculateSortDate(): Long {
        try {
            if (!viewIn.isNullOrEmpty()) {
                val ar = JsonUtils.gson.fromJson(viewIn, JsonArray::class.java)
                for (elem in ar) {
                    val obj = elem.asJsonObject
                    if (obj.has("section") && obj.get("section").asString.equals("community", true) && obj.has("sharedDate")) {
                        return obj.get("sharedDate").asLong
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return time
    }

    companion object {
        @JvmStatic
        fun createNews(map: HashMap<String?, String>, mRealm: Realm, user: RealmUser?, imageUrls: RealmList<String>?, isReply: Boolean = false): RealmNews {
            val shouldManageTransaction = !mRealm.isInTransaction
            if (shouldManageTransaction) {
                mRealm.beginTransaction()
            }

            val news = mRealm.createObject(RealmNews::class.java, "${UUID.randomUUID()}")
            news.message = map["message"]
            news.time = Date().time
            news.createdOn = user?.planetCode
            news.avatar = ""
            news.docType = "message"
            news.userName = user?.name
            news.parentCode = user?.parentCode
            news.messagePlanetCode = map["messagePlanetCode"]
            news.messageType = map["messageType"]
            news.sharedBy = ""
            if(isReply){
                news.viewIn = map["viewIn"]
            } else {
                news.viewIn = getViewInJson(map)
            }
            news.chat = map["chat"]?.toBoolean() ?: false

            try {
                news.updatedDate = map["updatedDate"]?.toLong() ?: 0
            } catch (e: Exception) {
                e.printStackTrace()
            }

            news.userId = user?.id
            news.replyTo = map["replyTo"] ?: ""
            news.user = JsonUtils.gson.toJson(user?.serialize())
            if (news.imageUrls == null) {
                news.imageUrls = RealmList()
            }
            imageUrls?.forEach { news.imageUrls?.add(it) }

            if (map.containsKey("news")) {
                val newsObj = map["news"]
                try {
                    val newsJsonString = newsObj?.replace("=", ":")
                    val newsJson = JsonUtils.gson.fromJson(newsJsonString, JsonObject::class.java)
                    news.newsId = JsonUtils.getString("_id", newsJson)
                    news.newsRev = JsonUtils.getString("_rev", newsJson)
                    news.newsUser = JsonUtils.getString("user", newsJson)
                    news.aiProvider = JsonUtils.getString("aiProvider", newsJson)
                    news.newsTitle = JsonUtils.getString("title", newsJson)
                    if (newsJson.has("conversations")) {
                        val conversationsElement = newsJson.get("conversations")
                        if (conversationsElement.isJsonPrimitive && conversationsElement.asJsonPrimitive.isString) {
                            val conversationsString = conversationsElement.asString
                            try {
                                val conversationsArray = JsonUtils.gson.fromJson(conversationsString, JsonArray::class.java)
                                if (conversationsArray.size() > 0) {
                                    val conversationsList = ArrayList<HashMap<String, String>>()
                                    conversationsArray.forEach { conversationElement ->
                                        val conversationObj = conversationElement.asJsonObject
                                        val conversationMap = HashMap<String, String>()
                                        conversationMap["query"] = conversationObj.get("query").asString
                                        conversationMap["response"] = conversationObj.get("response").asString
                                        conversationsList.add(conversationMap)
                                    }
                                    news.conversations = JsonUtils.gson.toJson(conversationsList)
                                }
                            } catch (e: JsonSyntaxException) {
                                e.printStackTrace()
                            }
                        }
                    }
                    news.newsCreatedDate = JsonUtils.getLong("createdDate", newsJson)
                    news.newsUpdatedDate = JsonUtils.getLong("updatedDate", newsJson)
                } catch (e: JsonSyntaxException) {
                    e.printStackTrace()
                }
            }

            if (shouldManageTransaction) {
                mRealm.commitTransaction()
            }
            return news
        }

        @JvmStatic
        fun getViewInJson(map: HashMap<String?, String>): String {
            val viewInArray = JsonArray()
            if (!TextUtils.isEmpty(map["viewInId"])) {
                val `object` = JsonObject()
                `object`.addProperty("_id", map["viewInId"])
                `object`.addProperty("section", map["viewInSection"])
                `object`.addProperty("name", map["name"])
                viewInArray.add(`object`)
            }
            return JsonUtils.gson.toJson(viewInArray)
        }
    }
}
