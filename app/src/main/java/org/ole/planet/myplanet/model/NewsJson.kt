package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import java.util.UUID
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import org.ole.planet.myplanet.utils.AppLog
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.toGson

// App-side Gson helpers for News, kept out of the Room entity.

private const val TAG = "News"

var News.parsedViewIn: JsonArray?
    get() = parsedViewInSlot as JsonArray?
    set(value) {
        parsedViewInSlot = value
    }

@Suppress("UNCHECKED_CAST")
var News.parsedImageUrls: List<JsonObject>?
    get() = parsedImageUrlsSlot as List<JsonObject>?
    set(value) {
        parsedImageUrlsSlot = value
    }

private data class ImagesCache(val raw: String?, val parsed: JsonArray)

val News.imagesArray: JsonArray
    get() {
        val currentImages = images
        val cache = imagesCache as ImagesCache?
        if (cache != null && cache.raw == currentImages) {
            return cache.parsed.deepCopy()
        }
        val parsed = if (currentImages == null) {
            JsonArray()
        } else {
            try {
                GsonUtils.gson.fromJson(currentImages, JsonArray::class.java) ?: JsonArray()
            } catch (e: Exception) {
                JsonArray()
            }
        }
        imagesCache = ImagesCache(currentImages, parsed)
        return parsed.deepCopy()
    }

val News.labelsArray: JsonArray
    get() = buildJsonArray {
        labels?.forEach { s ->
            add(s)
        }
    }.toGson()

fun News.setLabels(images: JsonArray) {
    val newLabels = ArrayList<String>()
    for (ob in images) {
        newLabels.add(ob.asString)
    }
    labels = newLabels
}

val News.isCommunityNews: Boolean
    get() {
        try {
            val array = parsedViewIn ?: if (!viewIn.isNullOrEmpty()) {
                GsonUtils.gson.fromJson(viewIn, JsonArray::class.java)
            } else null
            if (array != null) {
                for (e in array) {
                    val `object` = e.asJsonObject
                    if (`object`.has("section") && `object`["section"].asString.equals("community", ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "community section check failed", e)
        }
        return false
    }

fun News.calculateSortDate(): Long {
    try {
        if (!viewIn.isNullOrEmpty()) {
            val ar = parsedViewIn ?: GsonUtils.gson.fromJson(viewIn, JsonArray::class.java)
            for (elem in ar) {
                val obj = elem.asJsonObject
                if (GsonUtils.getString("section", obj).equals("community", true) && obj.has("sharedDate")) {
                    return GsonUtils.getLong("sharedDate", obj)
                }
            }
        }
    } catch (e: Exception) {
        AppLog.w(TAG, "calculateSortDate failed", e)
    }
    return time
}

/**
 * Builds an unmanaged [News] from a form map. The caller persists it via the DAO.
 */
fun News.Companion.createNews(
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
        AppLog.w(TAG, "updatedDate parse failed", e)
    }

    news.userId = user?.id
    news.replyTo = map["replyTo"] ?: ""
    news.user = GsonUtils.gson.toJson(user?.serialize())
    news.imageUrls = imageUrls?.toList() ?: emptyList()

    map["news"]?.let { newsObj ->
        try {
            val newsJsonString = newsObj.replace("=", ":")
            val newsJson = GsonUtils.gson.fromJson(newsJsonString, JsonObject::class.java)
            news.newsId = GsonUtils.getString("_id", newsJson)
            news.newsRev = GsonUtils.getString("_rev", newsJson)
            news.newsUser = GsonUtils.getString("user", newsJson)
            news.aiProvider = GsonUtils.getString("aiProvider", newsJson)
            news.newsTitle = GsonUtils.getString("title", newsJson)
            if (newsJson.has("conversations")) {
                val conversationsElement = newsJson.get("conversations")
                if (conversationsElement.isJsonPrimitive && conversationsElement.asJsonPrimitive.isString) {
                    val conversationsString = conversationsElement.asString
                    try {
                        val conversationsArray = GsonUtils.gson.fromJson(conversationsString, JsonArray::class.java)
                        if (!conversationsArray.isEmpty()) {
                            val conversationsList = ArrayList<HashMap<String, String>>()
                            conversationsArray.forEach { conversationElement ->
                                val conversationObj = conversationElement.asJsonObject
                                val conversationMap = HashMap<String, String>()
                                conversationMap["query"] = GsonUtils.getString("query", conversationObj)
                                conversationMap["response"] = GsonUtils.getString("response", conversationObj)
                                conversationsList.add(conversationMap)
                            }
                            news.conversations = GsonUtils.gson.toJson(conversationsList)
                        }
                    } catch (e: JsonSyntaxException) {
                        AppLog.w(TAG, "conversation parse failed", e)
                    }
                }
            }
            news.newsCreatedDate = GsonUtils.getLong("createdDate", newsJson)
            news.newsUpdatedDate = GsonUtils.getLong("updatedDate", newsJson)
        } catch (e: JsonSyntaxException) {
            AppLog.w(TAG, "news json parse failed", e)
        }
    }

    return news
}
