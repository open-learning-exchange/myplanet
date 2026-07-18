package org.ole.planet.myplanet.model

import android.text.TextUtils
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import java.util.Date
import java.util.UUID
import org.ole.planet.myplanet.utils.JsonUtils

/**
 * Room replacement for the former Realm `News` model (voices/discussion posts).
 *
 * `imageUrls` and `labels` (formerly `RealmList<String>`) are plain `List<String>` stored as JSON
 * via the shared [org.ole.planet.myplanet.data.room.Converters]. Persistence goes through
 * [org.ole.planet.myplanet.data.room.dao.NewsDao]. The class name is kept (`News`) so the
 * large voices UI surface is untouched; a later rename pass drops the `Realm` prefix.
 */
@Entity(tableName = "news", indices = [Index("userId"), Index("replyTo"), Index("_id")])
open class News {
    // @JvmField on id/_id so Room does not see ambiguous getId/get_id accessors.
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
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
    var imageUrls: List<String>? = null
    var images: String? = null
    var labels: List<String>? = null
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
    @Ignore
    var parsedViewIn: JsonArray? = null
    @Ignore
    var parsedConversations: List<Conversation>? = null
    @Ignore
    var parsedImageUrls: List<JsonObject>? = null
    @Ignore
    var rawViewIn: String? = null
    @Ignore
    var rawConversations: String? = null
    @Ignore
    var rawImageUrls: List<String>? = null

    @get:Ignore
    val imagesArray: JsonArray
        get() = if (images == null) JsonArray() else JsonUtils.gson.fromJson(images, JsonArray::class.java)

    @get:Ignore
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
        val newLabels = ArrayList<String>()
        for (ob in images) {
            newLabels.add(ob.asString)
        }
        labels = newLabels
    }

    @get:Ignore
    val messageWithoutMarkdown: String?
        get() {
            var ms = message
            for (ob in imagesArray) {
                ms = ms?.replace(JsonUtils.getString("markdown", ob.asJsonObject), "")
            }
            return ms
        }

    @get:Ignore
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
                    if (JsonUtils.getString("section", obj).equals("community", true) && obj.has("sharedDate")) {
                        return JsonUtils.getLong("sharedDate", obj)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return time
    }

    companion object {
        /**
         * Builds an unmanaged [News] from a form map. The caller persists it via the DAO.
         */
        fun createNews(
            map: HashMap<String?, String>,
            user: UserEntity?,
            imageUrls: List<String>?,
            isReply: Boolean = false
        ): News {
            val news = News()
            news.id = "${UUID.randomUUID()}"
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
            if (isReply) {
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
            news.imageUrls = imageUrls?.toList() ?: emptyList()

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
                                        conversationMap["query"] = JsonUtils.getString("query", conversationObj)
                                        conversationMap["response"] = JsonUtils.getString("response", conversationObj)
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

            return news
        }

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
