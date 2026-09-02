package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeedbackTest {

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
    fun testSetMessagesJsonArray() {
        val feedback = Feedback()
        val jsonArray = JsonArray()
        val obj = JsonObject()
        obj.addProperty("message", "Test message")
        jsonArray.add(obj)

        feedback.setMessages(jsonArray)
        assertEquals("[{\"message\":\"Test message\"}]", feedback.messages)
    }

    @Test
    fun testSetMessagesString() {
        val feedback = Feedback()
        val messageString = "[{\"message\":\"Test message\"}]"
        feedback.messages = messageString
        assertEquals(messageString, feedback.messages)
    }

    @Test
    fun testMessageListEmpty() {
        val feedback = Feedback()
        feedback.messages = ""
        assertNull(feedback.messageList)

        feedback.messages = null
        assertNull(feedback.messageList)
    }

    @Test
    fun testMessageListEmptyArray() {
        val feedback = Feedback()
        feedback.messages = "[]"
        val list = feedback.messageList
        assertNotNull(list)
        assertTrue(list!!.isEmpty())
    }

    @Test
    fun testMessageListWithElements() {
        val feedback = Feedback()
        val messageString = """
            [
              {"message": "msg0", "user": "user0", "time": "time0"},
              {"message": "msg1", "user": "user1", "time": "time1"},
              {"message": "msg2", "user": "user2", "time": "time2"}
            ]
        """.trimIndent()
        feedback.messages = messageString

        val list = feedback.messageList
        assertEquals(2, list?.size)
        assertEquals("msg1", list?.get(0)?.message)
        assertEquals("user1", list?.get(0)?.user)
        assertEquals("time1", list?.get(0)?.date)
        assertEquals("msg2", list?.get(1)?.message)
        assertEquals("user2", list?.get(1)?.user)
        assertEquals("time2", list?.get(1)?.date)
    }

    @Test
    fun testMessageEmpty() {
        val feedback = Feedback()
        feedback.messages = ""
        assertEquals("", feedback.message)

        feedback.messages = null
        assertEquals("", feedback.message)
    }

    @Test
    fun testMessageEmptyArray() {
        val feedback = Feedback()
        feedback.messages = "[]"
        assertEquals("", feedback.message)
    }

    @Test
    fun testMessageWithElements() {
        val feedback = Feedback()
        val messageString = """
            [
              {"message": "First message", "user": "user0", "time": "time0"},
              {"message": "Second message", "user": "user1", "time": "time1"}
            ]
        """.trimIndent()
        feedback.messages = messageString
        assertEquals("First message", feedback.message)
    }

    @Test
    fun testSerializeFeedback() {
        val feedback = Feedback().apply {
            title = "Test Title"
            source = "Test Source"
            status = "Open"
            priority = "High"
            owner = "Test Owner"
            openTime = 123456789L
            type = "Bug"
            url = "http://example.com"
            parentCode = "Parent123"
            state = "Active"
            item = "Item1"
            _id = "id123"
            _rev = "rev123"
        }
        val messageString = "[{\"message\":\"Test message\"}]"
        feedback.messages = messageString

        val serialized = Feedback.serializeFeedback(feedback)

        assertEquals("Test Title", serialized.get("title").asString)
        assertEquals("Test Source", serialized.get("source").asString)
        assertEquals("Open", serialized.get("status").asString)
        assertEquals("High", serialized.get("priority").asString)
        assertEquals("Test Owner", serialized.get("owner").asString)
        assertEquals(123456789L, serialized.get("openTime").asLong)
        assertEquals("Bug", serialized.get("type").asString)
        assertEquals("http://example.com", serialized.get("url").asString)
        assertEquals("Parent123", serialized.get("parentCode").asString)
        assertEquals("Active", serialized.get("state").asString)
        assertEquals("Item1", serialized.get("item").asString)
        assertEquals("id123", serialized.get("_id").asString)
        assertEquals("rev123", serialized.get("_rev").asString)

        val messagesArray = serialized.get("messages").asJsonArray
        assertEquals(1, messagesArray.size())
        assertEquals("Test message", messagesArray[0].asJsonObject.get("message").asString)
    }

    @Test
    fun testSerializeFeedbackWithInvalidMessagesException() {
        val feedback = Feedback()

        feedback.messages = "invalid json"

        mockkStatic(JsonParser::class)
        every { JsonParser.parseString(any()) } throws object : Exception("Test exception") {
            override fun printStackTrace() {
                // Do nothing to keep test logs clean
            }
        }

        val jsonObject = Feedback.serializeFeedback(feedback)

        // When an exception is thrown, the "messages" property is not added
        assertNull(jsonObject.get("messages"))
    }

    @Test
    fun testMessagesCachedAcrossAccesses() {
        val expectedArray = JsonArray().apply {
            add(JsonObject().apply { addProperty("message", "First message"); addProperty("user", "user0"); addProperty("time", "time0") })
            add(JsonObject().apply { addProperty("message", "Second message"); addProperty("user", "user1"); addProperty("time", "time1") })
        }
        val feedback = Feedback()
        feedback.messages = """[{"message": "First message", "user": "user0", "time": "time0"},
            {"message": "Second message", "user": "user1", "time": "time1"}]"""

        mockkStatic(JsonParser::class)
        every { JsonParser.parseString(any()) } returns expectedArray

        try {
            // Repeated reads of both derived views share one parse of the backing string.
            assertEquals("First message", feedback.message)
            assertEquals(1, feedback.messageList?.size)
            assertEquals("Second message", feedback.messageList?.get(0)?.message)
            repeat(5) { feedback.message }
            repeat(5) { feedback.messageList }

            verify(exactly = 1) { JsonParser.parseString(any()) }
        } finally {
            unmockkStatic(JsonParser::class)
        }
    }

    @Test
    fun testCacheInvalidatedOnMessagesChange() {
        val feedback = Feedback()
        feedback.messages = """[{"message": "first", "user": "u", "time": "t"}]"""
        assertEquals("first", feedback.message)

        feedback.messages = """[{"message": "second", "user": "u", "time": "t"}]"""
        // After reassigning messages the cache must be invalidated.
        assertEquals("second", feedback.message)
    }

    @Test
    fun testCacheInvalidatedOnSetMessages() {
        val feedback = Feedback()
        feedback.messages = """[{"message": "first", "user": "u", "time": "t"}]"""
        assertEquals("first", feedback.message)

        val jsonArray = JsonArray().apply {
            add(JsonObject().apply { addProperty("message", "second"); addProperty("user", "u"); addProperty("time", "t") })
        }
        feedback.setMessages(jsonArray)
        assertEquals("second", feedback.message)
    }
}
