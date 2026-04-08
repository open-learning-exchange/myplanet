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
            val str = arg<CharSequence?>(0)
            str == null || str.length == 0
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSetMessagesJsonArray() {
        val feedback = RealmFeedback()
        val jsonArray = JsonArray()
        val obj = JsonObject()
        obj.addProperty("message", "Test message")
        jsonArray.add(obj)

        feedback.setMessages(jsonArray)
        assertEquals("[{\"message\":\"Test message\"}]", feedback.messages)
    }

    @Test
    fun testSetMessagesString() {
        val feedback = RealmFeedback()
        val messageString = "[{\"message\":\"Test message\"}]"
        feedback.setMessages(messageString)
        assertEquals(messageString, feedback.messages)
    }

    @Test
    fun testMessageListEmpty() {
        val feedback = RealmFeedback()
        feedback.setMessages("")
        assertNull(feedback.messageList)

        feedback.setMessages(null as String?)
        assertNull(feedback.messageList)
    }

    @Test
    fun testMessageListWithElements() {
        val feedback = RealmFeedback()
        val messageString = """
            [
              {"message": "msg0", "user": "user0", "time": "time0"},
              {"message": "msg1", "user": "user1", "time": "time1"},
              {"message": "msg2", "user": "user2", "time": "time2"}
            ]
        """.trimIndent()
        feedback.setMessages(messageString)

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
        val feedback = RealmFeedback()
        feedback.setMessages("")
        assertEquals("", feedback.message)

        feedback.setMessages(null as String?)
        assertEquals("", feedback.message)
    }

    @Test
    fun testMessageWithElements() {
        val feedback = RealmFeedback()
        val messageString = """
            [
              {"message": "First message", "user": "user0", "time": "time0"},
              {"message": "Second message", "user": "user1", "time": "time1"}
            ]
        """.trimIndent()
        feedback.setMessages(messageString)
        assertEquals("First message", feedback.message)
    }

    @Test
    fun testSerializeFeedback() {
        val feedback = RealmFeedback().apply {
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
        feedback.setMessages(messageString)

        val serialized = RealmFeedback.serializeFeedback(feedback)

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
}
