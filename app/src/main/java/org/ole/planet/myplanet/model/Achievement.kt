package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Collections
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.KJson

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
        links = if (la == null) mutableListOf() else storedItems(la)
    }

    fun setOtherInfo(oi: JsonArray?) {
        otherInfo = if (oi == null) mutableListOf() else storedItems(oi)
    }

    fun setAchievements(ac: JsonArray) {
        achievements = storedItems(ac)
    }

    fun setReferences(of: JsonArray?) {
        references = if (of == null) mutableListOf() else storedItems(of)
    }

    companion object {
        internal const val CACHE_CAPACITY = 1000
        internal val parsedJsonCache: MutableMap<String, JsonElement> = Collections.synchronizedMap(
            object : LinkedHashMap<String, JsonElement>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, JsonElement>): Boolean = size > CACHE_CAPACITY
            }
        )

        /**
         * Each distinct item as the text Gson wrote for it (`Gson().toJson(element)`), which is
         * what the stored lists already hold. The array mirrors the Gson tree callers hand over,
         * so numbers print as written ([KJson.GsonNumbers.LITERAL]).
         */
        private fun storedItems(array: JsonArray): List<String> =
            array.mapTo(LinkedHashSet()) { KJson.gsonToJson(it, KJson.GsonNumbers.LITERAL) }.toList()

        private fun parseStringListToJsonArray(list: List<String>?): JsonArray =
            JsonArray(list.orEmpty().map { s ->
                parsedJsonCache[s] ?: (KJson.parseOrNull(s)?.also { parsedJsonCache[s] = it } ?: JsonNull)
            })

        fun fromJson(act: JsonObject): Achievement {
            return Achievement().apply {
                _id = JsonUtils.getString("_id", act)
                _rev = JsonUtils.getString("_rev", act)
                purpose = JsonUtils.getString("purpose", act)
                goals = JsonUtils.getString("goals", act)
                achievementsHeader = JsonUtils.getString("achievementsHeader", act)
                sendToNation = (act["sendToNation"] as? JsonPrimitive)?.content ?: "false"
                dateSortOrder = JsonUtils.getString("dateSortOrder", act)
                createdOn = JsonUtils.getString("createdOn", act)
                username = JsonUtils.getString("username", act)
                parentCode = JsonUtils.getString("parentCode", act)
                isUpdated = false
                setReferences(KJson.getJsonArray("references", act))
                setAchievements(KJson.getJsonArray("achievements", act))
                setLinks(KJson.getJsonArray("links", act))
                setOtherInfo(KJson.getJsonArray("otherInfo", act))
                resumeFileName = JsonUtils.getString("resumeFileName", act)
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
            put("references", sub.getReferencesArray())
            put("achievements", sub.achievementsArray)
            put("links", sub.linksArray)
            put("otherInfo", sub.otherInfoArray)
            put("resumeFileName", sub.resumeFileName ?: "")
        }

        fun createReference(name: String?, relation: String, phone: String, email: String): JsonObject = buildJsonObject {
            put("name", name)
            put("phone", phone)
            put("relationship", relation)
            put("email", email)
        }

    }
}
