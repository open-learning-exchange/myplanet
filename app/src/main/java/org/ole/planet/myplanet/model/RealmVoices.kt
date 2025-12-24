package org.ole.planet.myplanet.model

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.text.TextUtils
import androidx.core.content.edit
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
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.UrlUtils

open class RealmVoices : RealmObject() {
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
    var voicesId: String? = null
    var voicesRev: String? = null
    var voicesUser: String? = null
    var aiProvider: String? = null
    var voicesTitle: String? = null
    var conversations: String? = null
    var voicesCreatedDate: Long = 0
    var voicesUpdatedDate: Long = 0
    var chat: Boolean = false
    var isEdited: Boolean = false
    var editedTime: Long = 0
    var sharedBy: String? = null
    @Ignore
    var sortDate: Long = 0

    val imagesArray: JsonArray
        get() = if (images == null) JsonArray() else GsonUtils.gson.fromJson(images, JsonArray::class.java)

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

    val isCommunityVoices: Boolean
        get() {
            val array = GsonUtils.gson.fromJson(viewIn, JsonArray::class.java)
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
                val ar = GsonUtils.gson.fromJson(viewIn, JsonArray::class.java)
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
        private val concatenatedLinks = ArrayList<String>()

        @JvmStatic
        fun insert(mRealm: Realm, doc: JsonObject?) {
            var voices = mRealm.where(RealmVoices::class.java).equalTo("_id", JsonUtils.getString("_id", doc)).findFirst()
            if (voices == null) {
                voices = mRealm.createObject(RealmVoices::class.java, JsonUtils.getString("_id", doc))
            }
            voices?._rev = JsonUtils.getString("_rev", doc)
            voices?._id = JsonUtils.getString("_id", doc)
            voices?.viewableBy = JsonUtils.getString("viewableBy", doc)
            voices?.docType = JsonUtils.getString("docType", doc)
            voices?.avatar = JsonUtils.getString("avatar", doc)
            voices?.updatedDate = JsonUtils.getLong("updatedDate", doc)
            voices?.viewableId = JsonUtils.getString("viewableId", doc)
            voices?.createdOn = JsonUtils.getString("createdOn", doc)
            voices?.messageType = JsonUtils.getString("messageType", doc)
            voices?.messagePlanetCode = JsonUtils.getString("messagePlanetCode", doc)
            voices?.replyTo = JsonUtils.getString("replyTo", doc)
            voices?.parentCode = JsonUtils.getString("parentCode", doc)
            val user = JsonUtils.getJsonObject("user", doc)
            voices?.user = GsonUtils.gson.toJson(JsonUtils.getJsonObject("user", doc))
            voices?.userId = JsonUtils.getString("_id", user)
            voices?.userName = JsonUtils.getString("name", user)
            voices?.time = JsonUtils.getLong("time", doc)
            val images = JsonUtils.getJsonArray("images", doc)
            val message = JsonUtils.getString("message", doc)
            voices?.message = message
            val links = extractLinks(message)
            val baseUrl = UrlUtils.getUrl()
            for (link in links) {
                val concatenatedLink = "$baseUrl/$link"
                concatenatedLinks.add(concatenatedLink)
            }
            voices?.images = GsonUtils.gson.toJson(images)
            val labels = JsonUtils.getJsonArray("labels", doc)
            voices?.viewIn = GsonUtils.gson.toJson(JsonUtils.getJsonArray("viewIn", doc))
            voices?.setLabels(labels)
            voices?.chat = JsonUtils.getBoolean("chat", doc)

            val voicesObj = JsonUtils.getJsonObject("news", doc)
            voices?.voicesId = JsonUtils.getString("_id", voicesObj)
            voices?.voicesRev = JsonUtils.getString("_rev", voicesObj)
            voices?.voicesUser = JsonUtils.getString("user", voicesObj)
            voices?.aiProvider = JsonUtils.getString("aiProvider", voicesObj)
            voices?.voicesTitle = JsonUtils.getString("title", voicesObj)
            voices?.conversations = GsonUtils.gson.toJson(JsonUtils.getJsonArray("conversations", voicesObj))
            voices?.voicesCreatedDate = JsonUtils.getLong("createdDate", voicesObj)
            voices?.voicesUpdatedDate = JsonUtils.getLong("updatedDate", voicesObj)
            voices?.sharedBy = JsonUtils.getString("sharedBy", voicesObj)

            saveConcatenatedLinksToPrefs()
        }

        @JvmStatic
        fun serializeVoices(voices: RealmVoices): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("chat", voices.chat)
            `object`.addProperty("message", voices.message)
            if (voices._id != null) `object`.addProperty("_id", voices._id)
            if (voices._rev != null) `object`.addProperty("_rev", voices._rev)
            `object`.addProperty("time", voices.time)
            `object`.addProperty("createdOn", voices.createdOn)
            `object`.addProperty("docType", voices.docType)
            addViewIn(`object`, voices)
            `object`.addProperty("avatar", voices.avatar)
            `object`.addProperty("messageType", voices.messageType)
            `object`.addProperty("messagePlanetCode", voices.messagePlanetCode)
            `object`.addProperty("createdOn", voices.createdOn)
            `object`.addProperty("replyTo", voices.replyTo)
            `object`.addProperty("parentCode", voices.parentCode)
            `object`.add("images", voices.imagesArray)
            `object`.add("labels", voices.labelsArray)
            `object`.add("user", GsonUtils.gson.fromJson(voices.user, JsonObject::class.java))
            val voicesObject = JsonObject()
            voicesObject.addProperty("_id", voices.voicesId)
            voicesObject.addProperty("_rev", voices.voicesRev)
            voicesObject.addProperty("user", voices.voicesUser)
            voicesObject.addProperty("aiProvider", voices.aiProvider)
            voicesObject.addProperty("title", voices.voicesTitle)
            voicesObject.add("conversations", GsonUtils.gson.fromJson(voices.conversations, JsonArray::class.java))
            voicesObject.addProperty("createdDate", voices.voicesCreatedDate)
            voicesObject.addProperty("updatedDate", voices.voicesUpdatedDate)
            voicesObject.addProperty("sharedBy", voices.sharedBy)
            `object`.add("news", voicesObject)
            return `object`
        }

        private fun addViewIn(`object`: JsonObject, voices: RealmVoices) {
            if (!TextUtils.isEmpty(voices.viewableId)) {
                `object`.addProperty("viewableId", voices.viewableId)
                `object`.addProperty("viewableBy", voices.viewableBy)
            }
            if (!TextUtils.isEmpty(voices.viewIn)) {
                val ar = GsonUtils.gson.fromJson(voices.viewIn, JsonArray::class.java)
                if (ar.size() > 0) `object`.add("viewIn", ar)
            }
        }

        @JvmStatic
        fun createVoices(map: HashMap<String?, String>, mRealm: Realm, user: RealmUserModel?, imageUrls: RealmList<String>?, isReply: Boolean = false): RealmVoices {
            val shouldManageTransaction = !mRealm.isInTransaction
            if (shouldManageTransaction) {
                mRealm.beginTransaction()
            }

            val voices = mRealm.createObject(RealmVoices::class.java, "${UUID.randomUUID()}")
            voices.message = map["message"]
            voices.time = Date().time
            voices.createdOn = user?.planetCode
            voices.avatar = ""
            voices.docType = "message"
            voices.userName = user?.name
            voices.parentCode = user?.parentCode
            voices.messagePlanetCode = map["messagePlanetCode"]
            voices.messageType = map["messageType"]
            voices.sharedBy = ""
            if(isReply){
                voices.viewIn = map["viewIn"]
            } else {
                voices.viewIn = getViewInJson(map)
            }
            voices.chat = map["chat"]?.toBoolean() ?: false

            try {
                voices.updatedDate = map["updatedDate"]?.toLong() ?: 0
            } catch (e: Exception) {
                e.printStackTrace()
            }

            voices.userId = user?.id
            voices.replyTo = map["replyTo"] ?: ""
            voices.user = GsonUtils.gson.toJson(user?.serialize())
            if (voices.imageUrls == null) {
                voices.imageUrls = RealmList()
            }
            imageUrls?.forEach { voices.imageUrls?.add(it) }

            if (map.containsKey("news")) {
                val voicesObj = map["news"]
                try {
                    val voicesJsonString = voicesObj?.replace("=", ":")
                    val voicesJson = GsonUtils.gson.fromJson(voicesJsonString, JsonObject::class.java)
                    voices.voicesId = JsonUtils.getString("_id", voicesJson)
                    voices.voicesRev = JsonUtils.getString("_rev", voicesJson)
                    voices.voicesUser = JsonUtils.getString("user", voicesJson)
                    voices.aiProvider = JsonUtils.getString("aiProvider", voicesJson)
                    voices.voicesTitle = JsonUtils.getString("title", voicesJson)
                    if (voicesJson.has("conversations")) {
                        val conversationsElement = voicesJson.get("conversations")
                        if (conversationsElement.isJsonPrimitive && conversationsElement.asJsonPrimitive.isString) {
                            val conversationsString = conversationsElement.asString
                            try {
                                val conversationsArray = GsonUtils.gson.fromJson(conversationsString, JsonArray::class.java)
                                if (conversationsArray.size() > 0) {
                                    val conversationsList = ArrayList<HashMap<String, String>>()
                                    conversationsArray.forEach { conversationElement ->
                                        val conversationObj = conversationElement.asJsonObject
                                        val conversationMap = HashMap<String, String>()
                                        conversationMap["query"] = conversationObj.get("query").asString
                                        conversationMap["response"] = conversationObj.get("response").asString
                                        conversationsList.add(conversationMap)
                                    }
                                    voices.conversations = GsonUtils.gson.toJson(conversationsList)
                                }
                            } catch (e: JsonSyntaxException) {
                                e.printStackTrace()
                            }
                        }
                    }
                    voices.voicesCreatedDate = JsonUtils.getLong("createdDate", voicesJson)
                    voices.voicesUpdatedDate = JsonUtils.getLong("updatedDate", voicesJson)
                } catch (e: JsonSyntaxException) {
                    e.printStackTrace()
                }
            }

            if (shouldManageTransaction) {
                mRealm.commitTransaction()
            }
            return voices
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
            return GsonUtils.gson.toJson(viewInArray)
        }

        fun saveConcatenatedLinksToPrefs() {
            val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val existingJsonLinks = settings.getString("concatenated_links", null)
            val existingConcatenatedLinks = if (existingJsonLinks != null) {
                GsonUtils.gson.fromJson(existingJsonLinks, Array<String>::class.java).toMutableList()
            } else {
                mutableListOf()
            }
            val linksToProcess: List<String>
            synchronized(concatenatedLinks) {
                linksToProcess = concatenatedLinks.toList()
            }
            for (link in linksToProcess) {
                if (!existingConcatenatedLinks.contains(link)) {
                    existingConcatenatedLinks.add(link)
                }
            }
            val jsonConcatenatedLinks = GsonUtils.gson.toJson(existingConcatenatedLinks)
            settings.edit { putString("concatenated_links", jsonConcatenatedLinks) }
        }
    }
}
