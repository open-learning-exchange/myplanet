package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Collections
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.KotlinxJsonUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

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
            uniqueItems.add(GsonUtils.gson.toJson(el))
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
            uniqueItems.add(GsonUtils.gson.toJson(el))
        }
        otherInfo = uniqueItems.toList()
    }

    fun setAchievements(ac: JsonArray) {
        val uniqueItems = LinkedHashSet<String>()
        for (el in ac) {
            uniqueItems.add(GsonUtils.gson.toJson(el))
        }
        achievements = uniqueItems.toList()
    }

    fun setReferences(of: JsonArray?) {
        if (of == null) {
            references = mutableListOf()
            return
        }
        val uniqueItems = LinkedHashSet<String>()
        for (el in of) {
            uniqueItems.add(GsonUtils.gson.toJson(el))
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

        private fun parseStringListToJsonArray(list: List<String>?): JsonArray {
            val array = JsonArray()
            for (s in list ?: emptyList()) {
                var ob = parsedJsonCache[s]
                if (ob == null) {
                    ob = GsonUtils.gson.fromJson(s, JsonElement::class.java)
                    parsedJsonCache[s] = ob
                }
                array.add(ob?.deepCopy())
            }
            return array
        }

        fun fromJson(act: JsonObject): Achievement {
            val kAct = act.toKotlinx().jsonObject
            return Achievement().apply {
                _id = KotlinxJsonUtils.getString("_id", kAct)
                _rev = KotlinxJsonUtils.getString("_rev", kAct)
                purpose = KotlinxJsonUtils.getString("purpose", kAct)
                goals = KotlinxJsonUtils.getString("goals", kAct)
                achievementsHeader = KotlinxJsonUtils.getString("achievementsHeader", kAct)
                sendToNation = (kAct["sendToNation"] as? JsonPrimitive)?.content ?: "false"
                dateSortOrder = KotlinxJsonUtils.getString("dateSortOrder", kAct)
                createdOn = KotlinxJsonUtils.getString("createdOn", kAct)
                username = KotlinxJsonUtils.getString("username", kAct)
                parentCode = KotlinxJsonUtils.getString("parentCode", kAct)
                isUpdated = false
                setReferences(GsonUtils.getJsonArray("references", act))
                setAchievements(GsonUtils.getJsonArray("achievements", act))
                setLinks(GsonUtils.getJsonArray("links", act))
                setOtherInfo(GsonUtils.getJsonArray("otherInfo", act))
                resumeFileName = KotlinxJsonUtils.getString("resumeFileName", kAct)
            }
        }

        fun serialize(sub: Achievement): JsonObject = buildJsonObject {
            put("_id", sub._id)
            if (!sub._rev.isNullOrEmpty()) put("_rev", sub._rev)
            put("goals", sub.goals)
            put("purpose", sub.purpose)
            put("achievementsHeader", sub.achievementsHeader)
            put("sendToNation", sub.sendToNation?.toBoolean() ?: false)
            put("dateSortOrder", sub.dateSortOrder ?: "none")
            put("createdOn", sub.createdOn ?: "")
            put("username", sub.username ?: "")
            put("parentCode", sub.parentCode ?: "")
            put("references", sub.getReferencesArray().toKotlinx())
            put("achievements", sub.achievementsArray.toKotlinx())
            put("links", sub.linksArray.toKotlinx())
            put("otherInfo", sub.otherInfoArray.toKotlinx())
            put("resumeFileName", sub.resumeFileName ?: "")
        }.toGson()

        fun createReference(name: String?, relation: String, phone: String, email: String): JsonObject = buildJsonObject {
            put("name", name)
            put("phone", phone)
            put("relationship", relation)
            put("email", email)
        }.toGson()

    }
}
