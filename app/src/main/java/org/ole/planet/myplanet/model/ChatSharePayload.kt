package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.utils.JsonUtils

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
        serializedMap["conversations"] = JsonUtils.gson.toJson(serializedConversations)

        val map = HashMap<String?, String>()
        map["message"] = note
        map["viewInId"] = team?._id ?: ""
        map["viewInSection"] = section
        map["messageType"] = team?.teamType ?: ""
        map["messagePlanetCode"] = team?.teamPlanetCode ?: ""
        map["chat"] = "true"
        map["news"] = JsonUtils.gson.toJson(serializedMap)

        return map
    }

    private fun serializeConversation(conversation: Conversation): HashMap<String?, String> {
        val conversationMap = HashMap<String?, String>()
        conversationMap["query"] = conversation.query ?: ""
        conversationMap["response"] = conversation.response ?: ""
        return conversationMap
    }
}
