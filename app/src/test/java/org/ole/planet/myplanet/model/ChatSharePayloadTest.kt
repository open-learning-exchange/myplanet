package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatSharePayloadTest {

    @Test
    fun `exact outer key set assertion`() {
        val chat = ChatHistory().apply {
            _id = "chat_1"
            _rev = "1-rev"
            title = "Sample Chat"
            user = "user_1"
            aiProvider = "openai"
        }
        val map = ChatSharePayload.buildShareMap(
            chat = chat,
            note = "Check this chat out",
            team = null,
            section = "Teams",
            nowMillis = 1700000000000L
        )

        val expectedKeys = setOf(
            "message",
            "viewInId",
            "viewInSection",
            "messageType",
            "messagePlanetCode",
            "chat",
            "news"
        )
        assertEquals(expectedKeys, map.keys)
    }

    @Test
    fun `null team yields empty viewInId messageType and messagePlanetCode`() {
        val chat = ChatHistory()
        val map = ChatSharePayload.buildShareMap(
            chat = chat,
            note = "Note text",
            team = null,
            section = "Community",
            nowMillis = 1700000000000L
        )

        assertEquals("", map["viewInId"])
        assertEquals("", map["messageType"])
        assertEquals("", map["messagePlanetCode"])
        assertEquals("Community", map["viewInSection"])
        assertEquals("Note text", map["message"])
        assertEquals("true", map["chat"])
    }

    @Test
    fun `populated team fills viewInId messageType and messagePlanetCode`() {
        val team = TeamSummary(
            _id = "team_123",
            name = "Test Team",
            teamType = "team",
            teamPlanetCode = "planet_xyz",
            createdDate = null,
            type = null,
            status = null,
            teamId = null,
            description = null,
            services = null,
            rules = null
        )
        val chat = ChatHistory()
        val map = ChatSharePayload.buildShareMap(
            chat = chat,
            note = "Team note",
            team = team,
            section = "Teams",
            nowMillis = 1700000000000L
        )

        assertEquals("team_123", map["viewInId"])
        assertEquals("team", map["messageType"])
        assertEquals("planet_xyz", map["messagePlanetCode"])
    }

    @Test
    fun `null _id and _rev evaluate to empty strings not literal null`() {
        val chat = ChatHistory().apply {
            _id = null
            _rev = null
            title = null
            user = null
            aiProvider = null
        }
        val map = ChatSharePayload.buildShareMap(
            chat = chat,
            note = "test",
            team = null,
            section = "test",
            nowMillis = 1700000000000L
        )

        val newsJson = map["news"]
        assertNotNull(newsJson)

        val type = object : TypeToken<Map<String, String>>() {}.type
        val newsMap: Map<String, String> = Gson().fromJson(newsJson, type)

        assertEquals("", newsMap["_id"])
        assertEquals("", newsMap["_rev"])
        assertEquals("", newsMap["title"])
        assertEquals("", newsMap["user"])
        assertEquals("", newsMap["aiProvider"])
        assertEquals("1700000000000", newsMap["createdDate"])
        assertEquals("1700000000000", newsMap["updatedDate"])
    }

    @Test
    fun `conversation serialization round-trip through news json`() {
        val conversation1 = Conversation().apply {
            query = "What is Kotlin?"
            response = "Kotlin is a programming language."
        }
        val conversation2 = Conversation().apply {
            query = "What is Android?"
            response = "Android is a mobile operating system."
        }
        val chat = ChatHistory().apply {
            _id = "chat_100"
            _rev = "2-rev"
            title = "Tech Discussion"
            conversations = listOf(conversation1, conversation2)
        }

        val map = ChatSharePayload.buildShareMap(
            chat = chat,
            note = "Round trip test",
            team = null,
            section = "Community",
            nowMillis = 1700000000000L
        )

        val newsJson = map["news"]
        assertNotNull(newsJson)

        val type = object : TypeToken<Map<String, Any>>() {}.type
        val newsMap: Map<String, Any> = Gson().fromJson(newsJson, type)

        val conversationsJson = newsMap["conversations"] as String
        val listType = object : TypeToken<List<Map<String, String>>>() {}.type
        val conversationsList: List<Map<String, String>> = Gson().fromJson(conversationsJson, listType)

        assertEquals(2, conversationsList.size)
        assertEquals("What is Kotlin?", conversationsList[0]["query"])
        assertEquals("Kotlin is a programming language.", conversationsList[0]["response"])
        assertEquals("What is Android?", conversationsList[1]["query"])
        assertEquals("Android is a mobile operating system.", conversationsList[1]["response"])
    }
}
