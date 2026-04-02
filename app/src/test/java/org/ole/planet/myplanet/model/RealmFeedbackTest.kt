package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RealmFeedbackTest {

    @Before
    fun setup() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val str = firstArg<CharSequence?>()
            str == null || str.length == 0
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSetAndGetMessagesString() {
        val feedback = RealmFeedback()
        feedback.setMessages("test_message")
        assertEquals("test_message", feedback.messages)
    }

    @Test
    fun testSetMessagesJsonArray() {
        val feedback = RealmFeedback()
        val jsonArray = JsonArray()
        val jsonObject = JsonObject()
        jsonObject.addProperty("message", "hello")
        jsonArray.add(jsonObject)
        feedback.setMessages(jsonArray)

        assertEquals("[{\"message\":\"hello\"}]", feedback.messages)
    }

    @Test
    fun testMessageListWithEmptyMessages() {
        val feedback = RealmFeedback()
        feedback.setMessages("")
        assertNull(feedback.messageList)
    }

    @Test
    fun testMessageListWithValidMessages() {
        val feedback = RealmFeedback()
        val jsonStr = "[{\"message\":\"hello\",\"user\":\"user1\",\"time\":\"time1\"}, {\"message\":\"reply1\",\"user\":\"user2\",\"time\":\"time2\"}]"
        feedback.setMessages(jsonStr)

        val list = feedback.messageList
        assertEquals(1, list?.size)
        assertEquals("reply1", list?.get(0)?.message)
        assertEquals("user2", list?.get(0)?.user)
        assertEquals("time2", list?.get(0)?.date)
    }

    @Test
    fun testMessageWithEmptyMessages() {
        val feedback = RealmFeedback()
        feedback.setMessages("")
        assertEquals("", feedback.message)
    }

    @Test
    fun testMessageWithValidMessages() {
        val feedback = RealmFeedback()
        val jsonStr = "[{\"message\":\"hello\",\"user\":\"user1\",\"time\":\"time1\"}, {\"message\":\"reply1\",\"user\":\"user2\",\"time\":\"time2\"}]"
        feedback.setMessages(jsonStr)

        assertEquals("hello", feedback.message)
    }

    @Test
    fun testSerializeFeedback() {
        val feedback = RealmFeedback()
        feedback.title = "test_title"
        feedback.source = "test_source"
        feedback.status = "test_status"
        feedback.priority = "test_priority"
        feedback.owner = "test_owner"
        feedback.openTime = 12345L
        feedback.type = "test_type"
        feedback.url = "test_url"
        feedback.parentCode = "test_parentCode"
        feedback.state = "test_state"
        feedback.item = "test_item"
        feedback._id = "test_id"
        feedback._rev = "test_rev"
        feedback.setMessages("[{\"message\":\"hello\"}]")

        val jsonObject = RealmFeedback.serializeFeedback(feedback)

        assertEquals("test_title", jsonObject.get("title").asString)
        assertEquals("test_source", jsonObject.get("source").asString)
        assertEquals("test_status", jsonObject.get("status").asString)
        assertEquals("test_priority", jsonObject.get("priority").asString)
        assertEquals("test_owner", jsonObject.get("owner").asString)
        assertEquals(12345L, jsonObject.get("openTime").asLong)
        assertEquals("test_type", jsonObject.get("type").asString)
        assertEquals("test_url", jsonObject.get("url").asString)
        assertEquals("test_parentCode", jsonObject.get("parentCode").asString)
        assertEquals("test_state", jsonObject.get("state").asString)
        assertEquals("test_item", jsonObject.get("item").asString)
        assertEquals("test_id", jsonObject.get("_id").asString)
        assertEquals("test_rev", jsonObject.get("_rev").asString)
        assertEquals("hello", jsonObject.get("messages").asJsonArray.get(0).asJsonObject.get("message").asString)
    }

    @Test
    fun testSerializeFeedbackWithInvalidMessagesException() {
        val feedback = RealmFeedback()

        feedback.setMessages("invalid json")

        mockkStatic(com.google.gson.JsonParser::class)
        every { com.google.gson.JsonParser.parseString(any()) } throws object : Exception("Test exception") {
            override fun printStackTrace() {
                // Do nothing to keep test logs clean
            }
        }

        val jsonObject = RealmFeedback.serializeFeedback(feedback)

        // When an exception is thrown, the "messages" property is not added
        assertNull(jsonObject.get("messages"))
    }
}
