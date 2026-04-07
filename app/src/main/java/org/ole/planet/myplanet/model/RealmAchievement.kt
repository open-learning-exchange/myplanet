package org.ole.planet.myplanet.model

import android.text.TextUtils
import android.widget.EditText
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utils.JsonUtils
import android.util.LruCache

open class RealmAchievement : RealmObject() {
    var achievements: RealmList<String>? = null
    var references: RealmList<String>? = null
    var links: RealmList<String>? = null
    var otherInfo: RealmList<String>? = null
    var purpose: String? = null
    var achievementsHeader: String? = null
    var sendToNation: String? = null
    var _rev: String? = null
    @PrimaryKey
    var _id: String? = null
    var goals: String? = null
    var dateSortOrder: String? = null
    var createdOn: String? = null
    var username: String? = null
    var parentCode: String? = null
    var isUpdated: Boolean = false

    val achievementsArray: JsonArray
        get() = parseStringListToJsonArray(achievements)

    @io.realm.annotations.Ignore
    private var cachedReferencesArray: JsonArray? = null

    fun getReferencesArray(): JsonArray {
        return parseStringListToJsonArray(references)
    }

    val linksArray: JsonArray
        get() = parseStringListToJsonArray(links)

    val otherInfoArray: JsonArray
        get() = parseStringListToJsonArray(otherInfo)

    fun setLinks(la: JsonArray?) {
        links = RealmList()
        if (la == null) return
        for (el in la) {
            val e = JsonUtils.gson.toJson(el)
            if (links?.contains(e) != true) links?.add(e)
        }
    }

    fun setOtherInfo(oi: JsonArray?) {
        otherInfo = RealmList()
        if (oi == null) return
        for (el in oi) {
            val e = JsonUtils.gson.toJson(el)
            if (otherInfo?.contains(e) != true) otherInfo?.add(e)
        }
    }

    fun setAchievements(ac: JsonArray) {
        achievements = RealmList()
        for (el in ac) {
            val achievement = JsonUtils.gson.toJson(el)
            if (achievements?.contains(achievement) != true) {
                achievements?.add(achievement)
            }
        }
    }

    fun setReferences(of: JsonArray?) {
        cachedReferencesArray = null
        references = RealmList()
        if (of == null) return
        for (el in of) {
            val e = JsonUtils.gson.toJson(el)
            if (references?.contains(e) != true) {
                references?.add(e)
            }
        }
    }

    companion object {
        private val parsedJsonCache = LruCache<String, JsonElement>(1000)

        private fun parseStringListToJsonArray(list: RealmList<String>?): JsonArray {
            val array = JsonArray()
            for (s in list ?: emptyList()) {
                var ob = parsedJsonCache.get(s)
                if (ob == null) {
                    ob = JsonUtils.gson.fromJson(s, JsonElement::class.java)
                    parsedJsonCache.put(s, ob)
                }
                array.add(ob?.deepCopy())
            }
            return array
        }

        @JvmStatic
        fun serialize(sub: RealmAchievement): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("_id", sub._id)
            if (!TextUtils.isEmpty(sub._rev)) `object`.addProperty("_rev", sub._rev)
            `object`.addProperty("goals", sub.goals)
            `object`.addProperty("purpose", sub.purpose)
            `object`.addProperty("achievementsHeader", sub.achievementsHeader)
            `object`.addProperty("sendToNation", sub.sendToNation?.toBoolean() ?: false)
            `object`.addProperty("dateSortOrder", sub.dateSortOrder ?: "none")
            `object`.addProperty("createdOn", sub.createdOn ?: "")
            `object`.addProperty("username", sub.username ?: "")
            `object`.addProperty("parentCode", sub.parentCode ?: "")
            `object`.add("references", sub.getReferencesArray())
            `object`.add("achievements", sub.achievementsArray)
            `object`.add("links", sub.linksArray)
            `object`.add("otherInfo", sub.otherInfoArray)
            return `object`
        }

        @JvmStatic
        fun createReference(name: String?, relation: EditText, phone: EditText, email: EditText): JsonObject {
            val ob = JsonObject()
            ob.addProperty("name", name)
            ob.addProperty("phone", phone.text.toString())
            ob.addProperty("relationship", relation.text.toString())
            ob.addProperty("email", email.text.toString())
            return ob
        }

        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) {
            var achievement = mRealm.where(RealmAchievement::class.java)
                .equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
            if (achievement == null) {
                achievement = mRealm.createObject(RealmAchievement::class.java, JsonUtils.getString("_id", act))
            }
            achievement?._rev = JsonUtils.getString("_rev", act)
            achievement?.purpose = JsonUtils.getString("purpose", act)
            achievement?.goals = JsonUtils.getString("goals", act)
            achievement?.achievementsHeader = JsonUtils.getString("achievementsHeader", act)
            achievement?.sendToNation = act?.get("sendToNation")?.asString ?: "false"
            achievement?.dateSortOrder = JsonUtils.getString("dateSortOrder", act)
            achievement?.createdOn = JsonUtils.getString("createdOn", act)
            achievement?.username = JsonUtils.getString("username", act)
            achievement?.parentCode = JsonUtils.getString("parentCode", act)
            achievement?.isUpdated = false
            achievement?.setReferences(JsonUtils.getJsonArray("references", act))
            achievement?.setAchievements(JsonUtils.getJsonArray("achievements", act))
            achievement?.setLinks(JsonUtils.getJsonArray("links", act))
            achievement?.setOtherInfo(JsonUtils.getJsonArray("otherInfo", act))
        }
    }
}
