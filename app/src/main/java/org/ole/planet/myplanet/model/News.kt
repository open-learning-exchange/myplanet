package org.ole.planet.myplanet.model

import android.util.Log
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.KJson.GsonNumbers

/**
 * Room replacement for the former `News` model (voices/discussion posts).
 *
 * `imageUrls` and `labels` (formerly `RealmList<String>`) are plain `List<String>` stored as JSON
 * via the shared [org.ole.planet.myplanet.data.room.Converters]. Persistence goes through
 * [org.ole.planet.myplanet.data.room.dao.NewsDao]. The class name is kept (`News`) so the
 * large voices UI surface is untouched.
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
    var parsedImageUrls: List<JsonObject?>? = null
    @Ignore
    var rawViewIn: String? = null
    @Ignore
    var rawConversations: String? = null
    @Ignore
    var rawImageUrls: List<String>? = null
    @Ignore
    var parsedSharedTeamName: String? = null

    private data class ImagesCache(val raw: String?, val parsed: JsonArray)

    @Ignore
    @Volatile
    private var imagesCache: ImagesCache? = null

    @get:Ignore
    val imagesArray: JsonArray
        get() {
            val currentImages = images
            val cache = imagesCache
            if (cache != null && cache.raw == currentImages) {
                return cache.parsed
            }
            val parsed = try {
                KJson.parseArrayOrNull(currentImages) ?: JsonArray(emptyList())
            } catch (e: Exception) {
                JsonArray(emptyList())
            }
            imagesCache = ImagesCache(currentImages, parsed)
            return parsed
        }

    @get:Ignore
    val labelsArray: JsonArray
        get() = buildJsonArray {
            labels?.forEach { s ->
                add(s)
            }
        }

    fun updateMessage(newMessage: String) {
        this.message = newMessage
        this.isEdited = true
        this.editedTime = System.currentTimeMillis()
    }

    fun setLabels(images: JsonArray) {
        val newLabels = ArrayList<String>()
        for (ob in images) {
            newLabels.add(KJson.gsonAsString(ob, GsonNumbers.LITERAL))
        }
        labels = newLabels
    }

    @get:Ignore
    val isCommunityNews: Boolean
        get() {
            try {
                val array = parsedViewIn ?: if (!viewIn.isNullOrEmpty()) {
                    KJson.parseArrayOrNull(viewIn)
                } else null
                if (array != null) {
                    for (e in array) {
                        val `object` = KJson.asJsonObject(e)
                        if (`object`.containsKey("section") && KJson.gsonAsString(`object`["section"], GsonNumbers.LITERAL).equals("community", ignoreCase = true)) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "community section check failed", e)
            }
            return false
        }

    fun calculateSortDate(): Long {
        try {
            if (!viewIn.isNullOrEmpty()) {
                val ar = parsedViewIn ?: KJson.parseArrayOrNull(viewIn) ?: throw NullPointerException("blank viewIn")
                for (elem in ar) {
                    val obj = KJson.asJsonObject(elem)
                    if (KJson.getString("section", obj).equals("community", true) && obj.containsKey("sharedDate")) {
                        return KJson.getLong("sharedDate", obj)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "calculateSortDate failed", e)
        }
        return time
    }

    companion object {
        private const val TAG = "News"

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
            news.time = System.currentTimeMillis()
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
                Log.w(TAG, "updatedDate parse failed", e)
            }

            news.userId = user?.id
            news.replyTo = map["replyTo"] ?: ""
            news.user = KJson.gsonToJson(user?.serialize())
            news.imageUrls = imageUrls?.toList() ?: emptyList()

            map["news"]?.let { newsObj ->
                try {
                    val newsJsonString = newsObj.replace("=", ":")
                    val newsJson = KJson.parseObjectOrNull(newsJsonString) ?: throw NullPointerException("blank news payload")
                    news.newsId = KJson.getString("_id", newsJson)
                    news.newsRev = KJson.getString("_rev", newsJson)
                    news.newsUser = KJson.getString("user", newsJson)
                    news.aiProvider = KJson.getString("aiProvider", newsJson)
                    news.newsTitle = KJson.getString("title", newsJson)
                    if (newsJson.containsKey("conversations")) {
                        val conversationsElement = newsJson["conversations"]
                        if (conversationsElement is JsonPrimitive && conversationsElement.isString) {
                            val conversationsString = conversationsElement.content
                            try {
                                val conversationsArray = KJson.parseArrayOrNull(conversationsString) ?: throw NullPointerException("blank conversations")
                                if (conversationsArray.isNotEmpty()) {
                                    // HashMap, as Gson serialized it: its iteration order is the stored key order.
                                    val conversationsList = conversationsArray.map { conversationElement ->
                                        val conversationObj = KJson.asJsonObject(conversationElement)
                                        val conversationMap = HashMap<String, String>()
                                        conversationMap["query"] = KJson.getString("query", conversationObj)
                                        conversationMap["response"] = KJson.getString("response", conversationObj)
                                        JsonObject(conversationMap.mapValues { JsonPrimitive(it.value) })
                                    }
                                    news.conversations = KJson.gsonToJson(JsonArray(conversationsList))
                                }
                            } catch (e: SerializationException) {
                                Log.w(TAG, "conversation parse failed", e)
                            }
                        }
                    }
                    news.newsCreatedDate = KJson.getLong("createdDate", newsJson)
                    news.newsUpdatedDate = KJson.getLong("updatedDate", newsJson)
                } catch (e: SerializationException) {
                    Log.w(TAG, "news json parse failed", e)
                }
            }

            return news
        }

        fun getViewInJson(map: HashMap<String?, String>): String {
            val viewInArray = buildJsonArray {
                if (!map["viewInId"].isNullOrEmpty()) {
                    add(buildJsonObject {
                        put("_id", map["viewInId"])
                        put("section", map["viewInSection"])
                        put("name", map["name"])
                    })
                }
            }
            return viewInArray.toString()
        }
    }
}
