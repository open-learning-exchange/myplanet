package org.ole.planet.myplanet.model

import android.text.TextUtils
import android.util.LruCache
import android.widget.EditText
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.JsonUtils

@Entity(tableName = "achievements")
class Achievement {
    var achievements: List<String>? = null
    var references: List<String>? = null
    var links: List<String>? = null
    var otherInfo: List<String>? = null
    var purpose: String? = null
    var achievementsHeader: String? = null
    var sendToNation: String? = null
    var _rev: String? = null
    @PrimaryKey
    var _id: String = ""
    var goals: String? = null
    var dateSortOrder: String? = null
    var createdOn: String? = null
    var username: String? = null
    var parentCode: String? = null
    var isUpdated: Boolean = false
    var resumeFileName: String? = null

    val achievementsArray: JsonArray
        get() = parseStringListToJsonArray(achievements)

    @Ignore
    private var cachedReferencesArray: JsonArray? = null

    fun getReferencesArray(): JsonArray {
        return parseStringListToJsonArray(references)
    }

    val linksArray: JsonArray
        get() = parseStringListToJsonArray(links)

    val otherInfoArray: JsonArray
        get() = parseStringListToJsonArray(otherInfo)

    fun setLinks(la: JsonArray?) {
        links = mutableListOf()
        if (la == null) return
        for (el in la) {
            val e = JsonUtils.gson.toJson(el)
            if (links?.contains(e) != true) links = links.orEmpty() + e
        }
    }

    fun setOtherInfo(oi: JsonArray?) {
        otherInfo = mutableListOf()
        if (oi == null) return
        for (el in oi) {
            val e = JsonUtils.gson.toJson(el)
            if (otherInfo?.contains(e) != true) otherInfo = otherInfo.orEmpty() + e
        }
    }

    fun setAchievements(ac: JsonArray) {
        achievements = mutableListOf()
        for (el in ac) {
            val achievement = JsonUtils.gson.toJson(el)
            if (achievements?.contains(achievement) != true) {
                achievements = achievements.orEmpty() + achievement
            }
        }
    }

    fun setReferences(of: JsonArray?) {
        cachedReferencesArray = null
        references = mutableListOf()
        if (of == null) return
        for (el in of) {
            val e = JsonUtils.gson.toJson(el)
            if (references?.contains(e) != true) {
                references = references.orEmpty() + e
            }
        }
    }

    companion object {
        private val parsedJsonCache = LruCache<String, JsonElement>(1000)

        private fun parseStringListToJsonArray(list: List<String>?): JsonArray {
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

        fun fromJson(act: JsonObject): Achievement {
            return Achievement().apply {
                _id = JsonUtils.getString("_id", act)
                _rev = JsonUtils.getString("_rev", act)
                purpose = JsonUtils.getString("purpose", act)
                goals = JsonUtils.getString("goals", act)
                achievementsHeader = JsonUtils.getString("achievementsHeader", act)
                sendToNation = act.get("sendToNation")?.asString ?: "false"
                dateSortOrder = JsonUtils.getString("dateSortOrder", act)
                createdOn = JsonUtils.getString("createdOn", act)
                username = JsonUtils.getString("username", act)
                parentCode = JsonUtils.getString("parentCode", act)
                isUpdated = false
                setReferences(JsonUtils.getJsonArray("references", act))
                setAchievements(JsonUtils.getJsonArray("achievements", act))
                setLinks(JsonUtils.getJsonArray("links", act))
                setOtherInfo(JsonUtils.getJsonArray("otherInfo", act))
                resumeFileName = JsonUtils.getString("resumeFileName", act)
            }
        }

        fun serialize(sub: Achievement): JsonObject {
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
            `object`.addProperty("resumeFileName", sub.resumeFileName ?: "")
            return `object`
        }

        fun createReference(name: String?, relation: EditText, phone: EditText, email: EditText): JsonObject {
            val ob = JsonObject()
            ob.addProperty("name", name)
            ob.addProperty("phone", phone.text.toString())
            ob.addProperty("relationship", relation.text.toString())
            ob.addProperty("email", email.text.toString())
            return ob
        }

    }
}
