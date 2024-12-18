package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.IOException

class RealmNews : RealmObject {
    @PrimaryKey
    var id: String = ""
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

    val imagesArray: List<String>
        get() = images?.let { Gson().fromJson(it, List::class.java) as List<String> } ?: emptyList()

    val labelsArray: List<String>?
        get() = labels?.toList()

    fun addLabel(label: String) {
        if (label.isNotBlank() && labels?.contains(label) == false) {
            labels?.add(label)
        }
    }

    fun setLabels(labelsJson: JsonArray) {
        val labelsList = labelsJson.map { it.asString }
        labels?.clear()
        labels?.addAll(labelsList)
    }

    val messageWithoutMarkdown: String? get() {
        var ms = message
        imagesArray.forEach { markdown ->
            ms = ms?.replace(markdown, "")
        }
        return ms
    }

    val isCommunityNews: Boolean get() {
        val viewInArray = Gson().fromJson(viewIn, List::class.java) as? List<Map<String, String>> ?: return false
        return viewInArray.any { it["section"].equals("community", ignoreCase = true) }
    }

    companion object {
        val newsDataList: MutableList<Array<String>> = mutableListOf()

        suspend fun insert(realm: Realm, doc: JsonObject?) {
            realm.write {
                val existingNews = query<RealmNews>(RealmNews::class, "_id == $0", JsonUtils.getString("_id", doc)).first().find()

                val news = existingNews ?: copyToRealm(RealmNews().apply { _id = JsonUtils.getString("_id", doc) })

                news.apply {
                    _rev = JsonUtils.getString("_rev", doc)
                    viewableBy = JsonUtils.getString("viewableBy", doc)
                    docType = JsonUtils.getString("docType", doc)
                    avatar = JsonUtils.getString("avatar", doc)
                    updatedDate = JsonUtils.getLong("updatedDate", doc)
                    viewableId = JsonUtils.getString("viewableId", doc)
                    createdOn = JsonUtils.getString("createdOn", doc)
                    messageType = JsonUtils.getString("messageType", doc)
                    messagePlanetCode = JsonUtils.getString("messagePlanetCode", doc)
                    replyTo = JsonUtils.getString("replyTo", doc)
                    parentCode = JsonUtils.getString("parentCode", doc)

                    val user = JsonUtils.getJsonObject("user", doc)
                    this.user = Gson().toJson(user)
                    userId = JsonUtils.getString("_id", user)
                    userName = JsonUtils.getString("name", user)

                    time = JsonUtils.getLong("time", doc)
                    message = JsonUtils.getString("message", doc)
                    images = Gson().toJson(JsonUtils.getJsonArray("images", doc))
                    setLabels(JsonUtils.getJsonArray("labels", doc))
                    viewIn = Gson().toJson(JsonUtils.getJsonArray("viewIn", doc))
                    chat = JsonUtils.getBoolean("chat", doc)

                    val newsObj = JsonUtils.getJsonObject("news", doc)
                    newsId = JsonUtils.getString("_id", newsObj)
                    newsRev = JsonUtils.getString("_rev", newsObj)
                    newsUser = JsonUtils.getString("user", newsObj)
                    aiProvider = JsonUtils.getString("aiProvider", newsObj)
                    newsTitle = JsonUtils.getString("title", newsObj)
                    conversations = Gson().toJson(JsonUtils.getJsonArray("conversations", newsObj))
                    newsCreatedDate = JsonUtils.getLong("createdDate", newsObj)
                    newsUpdatedDate = JsonUtils.getLong("updatedDate", newsObj)
                }

                val csvRow = arrayOf(
                    JsonUtils.getString("_id", doc),
                    JsonUtils.getString("_rev", doc),
                    JsonUtils.getString("viewableBy", doc),
                    JsonUtils.getString("docType", doc),
                    JsonUtils.getString("avatar", doc),
                    JsonUtils.getLong("updatedDate", doc).toString(),
                    JsonUtils.getString("viewableId", doc),
                    JsonUtils.getString("createdOn", doc),
                    JsonUtils.getString("messageType", doc),
                    JsonUtils.getString("messagePlanetCode", doc),
                    JsonUtils.getString("replyTo", doc),
                    JsonUtils.getString("parentCode", doc),
                    JsonUtils.getString("user", doc),
                    JsonUtils.getString("time", doc),
                    JsonUtils.getString("message", doc),
                    JsonUtils.getString("images", doc),
                    JsonUtils.getString("labels", doc),
                    JsonUtils.getString("viewIn", doc),
                    JsonUtils.getBoolean("chat", doc).toString(),
                    JsonUtils.getString("news", doc)
                )
                newsDataList.add(csvRow)
            }
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                file.bufferedWriter().use { writer ->
                    val csvWriter = CSVWriter(writer)
                    csvWriter.writeNext(
                        arrayOf(
                            "_id", "_rev", "viewableBy", "docType", "avatar", "updatedDate",
                            "viewableId", "createdOn", "messageType", "messagePlanetCode",
                            "replyTo", "parentCode", "user", "time", "message",
                            "images", "labels", "viewIn", "chat", "news"
                        )
                    )
                    for (row in data) {
                        csvWriter.writeNext(row)
                    }
                    csvWriter.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun newsWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/news.csv", newsDataList)
        }

        fun serializeNews(news: RealmNews): JsonObject {
            val `object` = JsonObject().apply {
                addProperty("chat", news.chat)
                addProperty("message", news.message)
                if (news._id != null) addProperty("_id", news._id)
                if (news._rev != null) addProperty("_rev", news._rev)
                addProperty("time", news.time)
                addProperty("createdOn", news.createdOn)
                addProperty("docType", news.docType)
                addViewIn(this, news)
                addProperty("avatar", news.avatar)
                addProperty("messageType", news.messageType)
                addProperty("messagePlanetCode", news.messagePlanetCode)
                addProperty("createdOn", news.createdOn)
                addProperty("replyTo", news.replyTo)
                addProperty("parentCode", news.parentCode)
                add("images", Gson().toJsonTree(news.imagesArray))
                add("labels", Gson().toJsonTree(news.labelsArray))
                add("user", Gson().fromJson(news.user, JsonObject::class.java))

                val newsObject = JsonObject().apply {
                    addProperty("_id", news.newsId)
                    addProperty("_rev", news.newsRev)
                    addProperty("user", news.newsUser)
                    addProperty("aiProvider", news.aiProvider)
                    addProperty("title", news.newsTitle)
                    add("conversations", Gson().fromJson(news.conversations, JsonArray::class.java))
                    addProperty("createdDate", news.newsCreatedDate)
                    addProperty("updatedDate", news.newsUpdatedDate)
                }
                add("news", newsObject)
            }
            return `object`
        }

        private fun addViewIn(jsonObject: JsonObject, news: RealmNews) {
            if (!news.viewableId.isNullOrEmpty()) {
                jsonObject.addProperty("viewableId", news.viewableId)
                jsonObject.addProperty("viewableBy", news.viewableBy)
            }

            if (!news.viewIn.isNullOrEmpty()) {
                val viewInArray = Gson().fromJson(news.viewIn, JsonArray::class.java)
                if (viewInArray.size() > 0) {
                    jsonObject.add("viewIn", viewInArray)
                }
            }
        }

        suspend fun createNews(realm: Realm, map: Map<String?, String?>, realmUserModel: RealmUserModel?, imageUrl: RealmList<String>?): RealmNews {
            return realm.write {
                val news = copyToRealm(RealmNews())
                news.apply {
                    message = map["message"]
                    time = System.currentTimeMillis()
                    createdOn = realmUserModel?.planetCode
                    avatar = ""
                    docType = "message"
                    userName = realmUserModel?.name
                    parentCode = realmUserModel?.parentCode
                    messagePlanetCode = map["messagePlanetCode"]
                    messageType = map["messageType"]
                    viewIn = getViewInJson(map)
                    chat = map["chat"]?.toBoolean() == true
                    updatedDate = map["updatedDate"]?.toLongOrNull() ?: 0
                    userId = realmUserModel?.id
                    replyTo = map["replyTo"] ?: ""
                    user = Gson().toJson(realmUserModel?.serialize())
                    imageUrls = imageUrl

                    map["news"]?.let { newsStr ->
                        val newsJson = Gson().fromJson(newsStr.replace("=", ":"), JsonObject::class.java)
                        newsId = JsonUtils.getString("_id", newsJson)
                        newsRev = JsonUtils.getString("_rev", newsJson)
                        newsUser = JsonUtils.getString("user", newsJson)
                        aiProvider = JsonUtils.getString("aiProvider", newsJson)
                        newsTitle = JsonUtils.getString("title", newsJson)

                        newsJson.get("conversations")?.let { conversationsElement ->
                            if (conversationsElement.isJsonPrimitive && conversationsElement.asJsonPrimitive.isString) {
                                val conversationsArray = Gson().fromJson(
                                    conversationsElement.asString,
                                    JsonArray::class.java
                                )
                                conversations = Gson().toJson(
                                    conversationsArray.map { conv ->
                                        mapOf(
                                            "query" to conv.asJsonObject["query"].asString,
                                            "response" to conv.asJsonObject["response"].asString
                                        )
                                    }
                                )
                            }
                        }

                        newsCreatedDate = JsonUtils.getLong("createdDate", newsJson)
                        newsUpdatedDate = JsonUtils.getLong("updatedDate", newsJson)
                    }
                }
                news
            }
        }

        fun getViewInJson(map: Map<String?, String?>): String {
            val viewInArray = JsonArray()
            map["viewInId"]?.let { id ->
                val `object` = JsonObject().apply {
                    addProperty("_id", id)
                    addProperty("section", map["viewInSection"])
                }
                viewInArray.add(`object`)
            }
            return Gson().toJson(viewInArray)
        }
    }
}