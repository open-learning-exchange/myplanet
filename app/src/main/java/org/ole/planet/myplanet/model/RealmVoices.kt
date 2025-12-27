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

    val isCommunityVoice: Boolean
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
        private val concatenatedLinks = ArrayList<String>()

        @JvmStatic
        fun insert(mRealm: Realm, doc: JsonObject?) {
            var voice = mRealm.where(RealmVoices::class.java).equalTo("_id", JsonUtils.getString("_id", doc)).findFirst()
            if (voice == null) {
                voice = mRealm.createObject(RealmVoices::class.java, JsonUtils.getString("_id", doc))
            }
            voice?._rev = JsonUtils.getString("_rev", doc)
            voice?._id = JsonUtils.getString("_id", doc)
            voice?.viewableBy = JsonUtils.getString("viewableBy", doc)
            voice?.docType = JsonUtils.getString("docType", doc)
            voice?.avatar = JsonUtils.getString("avatar", doc)
            voice?.updatedDate = JsonUtils.getLong("updatedDate", doc)
            voice?.viewableId = JsonUtils.getString("viewableId", doc)
            voice?.createdOn = JsonUtils.getString("createdOn", doc)
            voice?.messageType = JsonUtils.getString("messageType", doc)
            voice?.messagePlanetCode = JsonUtils.getString("messagePlanetCode", doc)
            voice?.replyTo = JsonUtils.getString("replyTo", doc)
            voice?.parentCode = JsonUtils.getString("parentCode", doc)
            val user = JsonUtils.getJsonObject("user", doc)
            voice?.user = JsonUtils.gson.toJson(JsonUtils.getJsonObject("user", doc))
            voice?.userId = JsonUtils.getString("_id", user)
            voice?.userName = JsonUtils.getString("name", user)
            voice?.time = JsonUtils.getLong("time", doc)
            val images = JsonUtils.getJsonArray("images", doc)
            val message = JsonUtils.getString("message", doc)
            voice?.message = message
            val links = extractLinks(message)
            val baseUrl = UrlUtils.getUrl()
            for (link in links) {
                val concatenatedLink = "$baseUrl/$link"
                concatenatedLinks.add(concatenatedLink)
            }
            voice?.images = JsonUtils.gson.toJson(images)
            val labels = JsonUtils.getJsonArray("labels", doc)
            voice?.viewIn = JsonUtils.gson.toJson(JsonUtils.getJsonArray("viewIn", doc))
            voice?.setLabels(labels)
            voice?.chat = JsonUtils.getBoolean("chat", doc)

            val voiceObj = JsonUtils.getJsonObject("voices", doc)
            voice?.voicesId = JsonUtils.getString("_id", voiceObj)
            voice?.voicesRev = JsonUtils.getString("_rev", voiceObj)
            voice?.voicesUser = JsonUtils.getString("user", voiceObj)
            voice?.aiProvider = JsonUtils.getString("aiProvider", voiceObj)
            voice?.voicesTitle = JsonUtils.getString("title", voiceObj)
            voice?.conversations = JsonUtils.gson.toJson(JsonUtils.getJsonArray("conversations", voiceObj))
            voice?.voicesCreatedDate = JsonUtils.getLong("createdDate", voiceObj)
            voice?.voicesUpdatedDate = JsonUtils.getLong("updatedDate", voiceObj)
            voice?.sharedBy = JsonUtils.getString("sharedBy", voiceObj)

            saveConcatenatedLinksToPrefs()
        }

        @JvmStatic
        fun serializeVoices(voice: RealmVoices): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("chat", voice.chat)
            `object`.addProperty("message", voice.message)
            if (voice._id != null) `object`.addProperty("_id", voice._id)
            if (voice._rev != null) `object`.addProperty("_rev", voice._rev)
            `object`.addProperty("time", voice.time)
            `object`.addProperty("createdOn", voice.createdOn)
            `object`.addProperty("docType", voice.docType)
            addViewIn(`object`, voice)
            `object`.addProperty("avatar", voice.avatar)
            `object`.addProperty("messageType", voice.messageType)
            `object`.addProperty("messagePlanetCode", voice.messagePlanetCode)
            `object`.addProperty("createdOn", voice.createdOn)
            `object`.addProperty("replyTo", voice.replyTo)
            `object`.addProperty("parentCode", voice.parentCode)
            `object`.add("images", voice.imagesArray)
            `object`.add("labels", voice.labelsArray)
            `object`.add("user", JsonUtils.gson.fromJson(voice.user, JsonObject::class.java))
            val voiceObject = JsonObject()
            voiceObject.addProperty("_id", voice.voicesId)
            voiceObject.addProperty("_rev", voice.voicesRev)
            voiceObject.addProperty("user", voice.voicesUser)
            voiceObject.addProperty("aiProvider", voice.aiProvider)
            voiceObject.addProperty("title", voice.voicesTitle)
            voiceObject.add("conversations", JsonUtils.gson.fromJson(voice.conversations, JsonArray::class.java))
            voiceObject.addProperty("createdDate", voice.voicesCreatedDate)
            voiceObject.addProperty("updatedDate", voice.voicesUpdatedDate)
            voiceObject.addProperty("sharedBy", voice.sharedBy)
            `object`.add("voices", voiceObject)
            return `object`
        }

        private fun addViewIn(`object`: JsonObject, voice: RealmVoices) {
            if (!TextUtils.isEmpty(voice.viewableId)) {
                `object`.addProperty("viewableId", voice.viewableId)
                `object`.addProperty("viewableBy", voice.viewableBy)
            }
            if (!TextUtils.isEmpty(voice.viewIn)) {
                val ar = JsonUtils.gson.fromJson(voice.viewIn, JsonArray::class.java)
                if (ar.size() > 0) `object`.add("viewIn", ar)
            }
        }

        @JvmStatic
        fun createVoices(map: HashMap<String?, String>, mRealm: Realm, user: RealmUserModel?, imageUrls: RealmList<String>?, isReply: Boolean = false): RealmVoices {
            val shouldManageTransaction = !mRealm.isInTransaction
            if (shouldManageTransaction) {
                mRealm.beginTransaction()
            }

            val voice = mRealm.createObject(RealmVoices::class.java, "${UUID.randomUUID()}")
            voice.message = map["message"]
            voice.time = Date().time
            voice.createdOn = user?.planetCode
            voice.avatar = ""
            voice.docType = "message"
            voice.userName = user?.name
            voice.parentCode = user?.parentCode
            voice.messagePlanetCode = map["messagePlanetCode"]
            voice.messageType = map["messageType"]
            voice.sharedBy = ""
            if(isReply){
                voice.viewIn = map["viewIn"]
            } else {
                voice.viewIn = getViewInJson(map)
            }
            voice.chat = map["chat"]?.toBoolean() ?: false

            try {
                voice.updatedDate = map["updatedDate"]?.toLong() ?: 0
            } catch (e: Exception) {
                e.printStackTrace()
            }

            voice.userId = user?.id
            voice.replyTo = map["replyTo"] ?: ""
            voice.user = JsonUtils.gson.toJson(user?.serialize())
            if (voice.imageUrls == null) {
                voice.imageUrls = RealmList()
            }
            imageUrls?.forEach { voice.imageUrls?.add(it) }

            if (map.containsKey("voices")) {
                val voiceObj = map["voices"]
                try {
                    val voiceJsonString = voiceObj?.replace("=", ":")
                    val voiceJson = JsonUtils.gson.fromJson(voiceJsonString, JsonObject::class.java)
                    voice.voicesId = JsonUtils.getString("_id", voiceJson)
                    voice.voicesRev = JsonUtils.getString("_rev", voiceJson)
                    voice.voicesUser = JsonUtils.getString("user", voiceJson)
                    voice.aiProvider = JsonUtils.getString("aiProvider", voiceJson)
                    voice.voicesTitle = JsonUtils.getString("title", voiceJson)
                    if (voiceJson.has("conversations")) {
                        val conversationsElement = voiceJson.get("conversations")
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
                                    voice.conversations = JsonUtils.gson.toJson(conversationsList)
                                }
                            } catch (e: JsonSyntaxException) {
                                e.printStackTrace()
                            }
                        }
                    }
                    voice.voicesCreatedDate = JsonUtils.getLong("createdDate", voiceJson)
                    voice.voicesUpdatedDate = JsonUtils.getLong("updatedDate", voiceJson)
                } catch (e: JsonSyntaxException) {
                    e.printStackTrace()
                }
            }

            if (shouldManageTransaction) {
                mRealm.commitTransaction()
            }
            return voice
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

        fun saveConcatenatedLinksToPrefs() {
            val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val existingJsonLinks = settings.getString("concatenated_links", null)
            val existingConcatenatedLinks = if (existingJsonLinks != null) {
                JsonUtils.gson.fromJson(existingJsonLinks, Array<String>::class.java).toMutableList()
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
            val jsonConcatenatedLinks = JsonUtils.gson.toJson(existingConcatenatedLinks)
            settings.edit { putString("concatenated_links", jsonConcatenatedLinks) }
        }
    }
}
