package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Collections
import org.ole.planet.myplanet.utils.JsonUtils

@Entity(tableName = "achievements", indices = [androidx.room.Index("isUpdated")])
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
        if (la == null) {
            links = mutableListOf()
            return
        }
        val uniqueItems = LinkedHashSet<String>()
        for (el in la) {
            uniqueItems.add(JsonUtils.gson.toJson(el))
        }
        links = uniqueItems.toList()
    }

    fun setOtherInfo(oi: JsonArray?) {
        if (oi == null) {
            otherInfo = mutableListOf()
            return
        }
        val uniqueItems = LinkedHashSet<String>()
        for (el in oi) {
            uniqueItems.add(JsonUtils.gson.toJson(el))
        }
        otherInfo = uniqueItems.toList()
    }

    fun setAchievements(ac: JsonArray) {
        val uniqueItems = LinkedHashSet<String>()
        for (el in ac) {
            uniqueItems.add(JsonUtils.gson.toJson(el))
        }
        achievements = uniqueItems.toList()
    }

    fun setReferences(of: JsonArray?) {
        cachedReferencesArray = null
        if (of == null) {
            references = mutableListOf()
            return
        }
        val uniqueItems = LinkedHashSet<String>()
        for (el in of) {
            uniqueItems.add(JsonUtils.gson.toJson(el))
        }
        references = uniqueItems.toList()
    }

    companion object {
        internal const val CACHE_CAPACITY = 1000
        internal val parsedJsonCache: MutableMap<String, JsonElement> = Collections.synchronizedMap(
            object : LinkedHashMap<String, JsonElement>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, JsonElement>): Boolean = size > CACHE_CAPACITY
            }
        )

        /**
         * Parses a list of JSON strings into a [JsonArray].
         *
         * Cached elements are returned directly without deep copying. Callers must not mutate
         * the elements contained in the returned array.
         */
        private fun parseStringListToJsonArray(list: List<String>?): JsonArray {
            val array = JsonArray()
            for (s in list ?: emptyList()) {
                var ob = parsedJsonCache[s]
                if (ob == null) {
                    ob = JsonUtils.gson.fromJson(s, JsonElement::class.java)
                    parsedJsonCache[s] = ob
                }
                array.add(ob)
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
            if (!sub._rev.isNullOrEmpty()) `object`.addProperty("_rev", sub._rev)
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

        fun createReference(name: String?, relation: String, phone: String, email: String): JsonObject {
            val ob = JsonObject()
            ob.addProperty("name", name)
            ob.addProperty("phone", phone)
            ob.addProperty("relationship", relation)
            ob.addProperty("email", email)
            return ob
        }

    }
}
