package org.ole.planet.myplanet.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.ole.planet.myplanet.utils.KJson

object ChatSharePayload {
    fun buildShareMap(
        chat: ChatHistory,
        note: String,
        team: TeamSummary?,
        section: String,
        nowMillis: Long
    ): HashMap<String?, String> {
        val serializedConversations = chat.conversations?.map { serializeConversation(it) }
        val serializedMap = HashMap<String?, String>()
        serializedMap["_id"] = chat._id ?: ""
        serializedMap["_rev"] = chat._rev ?: ""
        serializedMap["title"] = (chat.title ?: "").trim()
        serializedMap["user"] = chat.user ?: ""
        serializedMap["aiProvider"] = chat.aiProvider ?: ""
        serializedMap["createdDate"] = "$nowMillis"
        serializedMap["updatedDate"] = "$nowMillis"
        serializedMap["conversations"] = KJson.gsonToJson(serializedConversations?.let { JsonArray(it.map(::asJson)) } ?: JsonNull)

        val map = HashMap<String?, String>()
        map["message"] = note
        map["viewInId"] = team?._id ?: ""
        map["viewInSection"] = section
        map["messageType"] = team?.teamType ?: ""
        map["messagePlanetCode"] = team?.teamPlanetCode ?: ""
        map["chat"] = "true"
        // HTML-safe like Gson: News.createNews turns every '=' into ':' and relies on '=' being escaped.
        map["news"] = KJson.gsonToJson(asJson(serializedMap))

        return map
    }

    /** A HashMap as Gson wrote it: keys in the map's iteration order. */
    private fun asJson(map: HashMap<String?, String>): JsonObject =
        JsonObject(LinkedHashMap<String, JsonPrimitive>().apply { map.forEach { (k, v) -> put(k.toString(), JsonPrimitive(v)) } })

    private fun serializeConversation(conversation: Conversation): HashMap<String?, String> {
        val conversationMap = HashMap<String?, String>()
        conversationMap["query"] = conversation.query ?: ""
        conversationMap["response"] = conversation.response ?: ""
        return conversationMap
    }
}
