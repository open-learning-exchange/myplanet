package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Collections
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

// App-side Gson helpers for Achievement, kept out of the Room entity.

private const val PARSED_JSON_CACHE_CAPACITY = 1000

private val parsedJsonCacheMap: MutableMap<String, JsonElement> = Collections.synchronizedMap(
    object : LinkedHashMap<String, JsonElement>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, JsonElement>): Boolean = size > PARSED_JSON_CACHE_CAPACITY
    }
)

internal val Achievement.Companion.CACHE_CAPACITY: Int
    get() = PARSED_JSON_CACHE_CAPACITY

internal val Achievement.Companion.parsedJsonCache: MutableMap<String, JsonElement>
    get() = parsedJsonCacheMap

val Achievement.achievementsArray: JsonArray
    get() = parseStringListToJsonArray(achievements)

fun Achievement.getReferencesArray(): JsonArray {
    return parseStringListToJsonArray(references)
}

val Achievement.linksArray: JsonArray
    get() = parseStringListToJsonArray(links)

val Achievement.otherInfoArray: JsonArray
    get() = parseStringListToJsonArray(otherInfo)

fun Achievement.setLinks(la: JsonArray?) {
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

fun Achievement.setOtherInfo(oi: JsonArray?) {
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

fun Achievement.setAchievements(ac: JsonArray) {
    val uniqueItems = LinkedHashSet<String>()
    for (el in ac) {
        uniqueItems.add(GsonUtils.gson.toJson(el))
    }
    achievements = uniqueItems.toList()
}

fun Achievement.setReferences(of: JsonArray?) {
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

private fun parseStringListToJsonArray(list: List<String>?): JsonArray {
    val array = JsonArray()
    for (s in list ?: emptyList()) {
        var ob = parsedJsonCacheMap[s]
        if (ob == null) {
            ob = GsonUtils.gson.fromJson(s, JsonElement::class.java)
            parsedJsonCacheMap[s] = ob
        }
        array.add(ob?.deepCopy())
    }
    return array
}

fun Achievement.Companion.fromJson(act: JsonObject): Achievement {
    val kAct = act.toKotlinx().jsonObject
    return Achievement().apply {
        _id = JsonUtils.getString("_id", kAct)
        _rev = JsonUtils.getString("_rev", kAct)
        purpose = JsonUtils.getString("purpose", kAct)
        goals = JsonUtils.getString("goals", kAct)
        achievementsHeader = JsonUtils.getString("achievementsHeader", kAct)
        sendToNation = (kAct["sendToNation"] as? JsonPrimitive)?.content ?: "false"
        dateSortOrder = JsonUtils.getString("dateSortOrder", kAct)
        createdOn = JsonUtils.getString("createdOn", kAct)
        username = JsonUtils.getString("username", kAct)
        parentCode = JsonUtils.getString("parentCode", kAct)
        isUpdated = false
        setReferences(GsonUtils.getJsonArray("references", act))
        setAchievements(GsonUtils.getJsonArray("achievements", act))
        setLinks(GsonUtils.getJsonArray("links", act))
        setOtherInfo(GsonUtils.getJsonArray("otherInfo", act))
        resumeFileName = JsonUtils.getString("resumeFileName", kAct)
    }
}

fun Achievement.Companion.serialize(sub: Achievement): JsonObject = buildJsonObject {
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

fun Achievement.Companion.createReference(name: String?, relation: String, phone: String, email: String): JsonObject = buildJsonObject {
    put("name", name)
    put("phone", phone)
    put("relationship", relation)
    put("email", email)
}.toGson()
