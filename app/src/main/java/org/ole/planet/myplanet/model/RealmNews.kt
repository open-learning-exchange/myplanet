package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.UUID

open class RealmNews : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    private var _id: String? = null
    private var _rev: String? = null
    @JvmField
    var userId: String? = null
    @JvmField
    var user: String? = null
    @JvmField
    var message: String? = null
    @JvmField
    var docType: String? = null
    @JvmField
    var viewableBy: String? = null
    @JvmField
    var viewableId: String? = null
    @JvmField
    var avatar: String? = null
    @JvmField
    var replyTo: String? = null
    @JvmField
    var userName: String? = null
    @JvmField
    var messagePlanetCode: String? = null
    @JvmField
    var messageType: String? = null
    @JvmField
    var updatedDate: Long = 0
    @JvmField
    var time: Long = 0
    @JvmField
    var createdOn: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var imageUrls: RealmList<String>? = null
    @JvmField
    var images: String? = null
    @JvmField
    var labels: RealmList<String>? = null
    @JvmField
    var viewIn: String? = null
    val imagesArray: JsonArray
        get() = if (images == null) JsonArray() else Gson().fromJson(images, JsonArray::class.java)
    val labelsArray: JsonArray
        get() {
            val array = JsonArray()
            for (s in labels!!) {
                array.add(s)
            }
            return array
        }

    fun addLabel(label: String?) {
        if (!labels!!.contains(label)) {
            Utilities.log("Added")
            labels!!.add(label)
        }
    }

    fun setLabels(images: JsonArray) {
        labels = RealmList()
        for (ob in images) {
            labels!!.add(ob.asString)
        }
    }

    fun get_id(): String? {
        return _id
    }

    fun set_id(_id: String?) {
        this._id = _id
    }

    fun get_rev(): String? {
        return _rev
    }

    fun set_rev(_rev: String?) {
        this._rev = _rev
    }

    val messageWithoutMarkdown: String?
        get() {
            var ms = message
            Utilities.log(ms)
            for (ob in imagesArray) {
                ms = ms!!.replace(JsonUtils.getString("markdown", ob.asJsonObject), "")
            }
            return ms
        }
    val isCommunityNews: Boolean
        get() {
            val array = Gson().fromJson(viewIn, JsonArray::class.java)
            var isCommunity = false
            for (e in array) {
                val `object` = e.asJsonObject
                if (`object`.has("section") && `object`["section"].asString.equals(
                        "community",
                        ignoreCase = true
                    )
                ) {
                    isCommunity = true
                    break
                }
            }
            return isCommunity
        }

    companion object {
        fun insert(mRealm: Realm, doc: JsonObject?) {
            Utilities.log("sync nnews " + Gson().toJson(doc))
            var news = mRealm.where(RealmNews::class.java).equalTo("_id", JsonUtils.getString("_id", doc)).findFirst()
            if (news == null) {
                news = mRealm.createObject(RealmNews::class.java, JsonUtils.getString("_id", doc))
            }
            news!!.set_rev(JsonUtils.getString("_rev", doc))
            news.set_id(JsonUtils.getString("_id", doc))
            news.viewableBy = JsonUtils.getString("viewableBy", doc)
            news.docType = JsonUtils.getString("docType", doc)
            news.avatar = JsonUtils.getString("avatar", doc)
            news.updatedDate = JsonUtils.getLong("updatedDate", doc)
            news.viewableId = JsonUtils.getString("viewableId", doc)
            news.createdOn = JsonUtils.getString("createdOn", doc)
            news.messageType = JsonUtils.getString("messageType", doc)
            news.messagePlanetCode = JsonUtils.getString("messagePlanetCode", doc)
            news.replyTo = JsonUtils.getString("replyTo", doc)
            news.parentCode = JsonUtils.getString("parentCode", doc)
            val user = JsonUtils.getJsonObject("user", doc)
            news.user = Gson().toJson(JsonUtils.getJsonObject("user", doc))
            news.userId = JsonUtils.getString("_id", user)
            news.userName = JsonUtils.getString("name", user)
            news.time = JsonUtils.getLong("time", doc)
            val images = JsonUtils.getJsonArray("images", doc)
            val message = JsonUtils.getString("message", doc)
            news.message = message
            news.images = Gson().toJson(images)
            val labels = JsonUtils.getJsonArray("labels", doc)
            news.viewIn = Gson().toJson(JsonUtils.getJsonArray("viewIn", doc))
            news.setLabels(labels)
        }

        @JvmStatic
        fun serializeNews(news: RealmNews, user: RealmUserModel?): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("message", news.message)
            if (news.get_id() != null) `object`.addProperty("_id", news.get_id())
            if (news.get_rev() != null) `object`.addProperty("_rev", news.get_rev())
            `object`.addProperty("time", news.time)
            `object`.addProperty("createdOn", news.createdOn)
            `object`.addProperty("docType", news.docType)
            addViewIn(`object`, news)
            `object`.addProperty("avatar", news.avatar)
            `object`.addProperty("messageType", news.messageType)
            `object`.addProperty("messagePlanetCode", news.messagePlanetCode)
            `object`.addProperty("createdOn", news.createdOn)
            `object`.addProperty("replyTo", news.replyTo)
            `object`.addProperty("parentCode", news.parentCode)
            `object`.add("images", news.imagesArray)
            `object`.add("labels", news.labelsArray)
            `object`.add("user", Gson().fromJson(news.user, JsonObject::class.java))
            return `object`
        }

        private fun addViewIn(`object`: JsonObject, news: RealmNews) {
            if (!TextUtils.isEmpty(news.viewableId)) {
                `object`.addProperty("viewableId", news.viewableId)
                `object`.addProperty("viewableBy", news.viewableBy)
            }
            if (!TextUtils.isEmpty(news.viewIn)) {
                val ar = Gson().fromJson(news.viewIn, JsonArray::class.java)
                if (ar.size() > 0) `object`.add("viewIn", ar)
            }
        }

        @JvmStatic
        fun createNews(map: HashMap<String?, String>, mRealm: Realm, user: RealmUserModel, imageUrls: RealmList<String>?): RealmNews {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            val news = mRealm.createObject(RealmNews::class.java, UUID.randomUUID().toString())
            news.message = map["message"]
            news.time = Date().time
            news.createdOn = user.planetCode
            news.avatar = ""
            news.docType = "message"
            news.userName = user.name
            news.parentCode = user.parentCode
            news.messagePlanetCode = map["messagePlanetCode"]
            news.messageType = map["messageType"]
            news.viewIn = getViewInJson(map)
            try {
                news.updatedDate = map["updatedDate"]!!.toLong()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            news.userId = user.id
            news.replyTo = if (map.containsKey("replyTo")) map["replyTo"] else ""
            news.user = Gson().toJson(user.serialize())
            news.imageUrls = imageUrls
            mRealm.commitTransaction()
            return news
        }

        fun getViewInJson(map: HashMap<String?, String>): String {
            val viewInArray = JsonArray()
            if (!TextUtils.isEmpty(map["viewInId"])) {
                val `object` = JsonObject()
                `object`.addProperty("_id", map["viewInId"])
                `object`.addProperty("section", map["viewInSection"])
                viewInArray.add(`object`)
            }
            return Gson().toJson(viewInArray)
        }
    }
}
