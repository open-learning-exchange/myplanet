package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TeamTaskTest {

    @Before
    fun setup() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val str = arg<CharSequence?>(0)
            str == null || str.isEmpty()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSerialize_withUser() {
        val task = TeamTask().apply {
            _id = "task123"
            _rev = "rev123"
            title = "Task Title"
            deadline = 1620000000000L
            description = "Task Description"
            completed = true
            completedTime = 1620000000000L
            sync = "{\"syncKey\":\"syncValue\"}"
            link = "{\"linkKey\":\"linkValue\"}"
        }

        val userEntity = mockk<UserEntity>()
        val userJson = JsonObject().apply { addProperty("userName", "John Doe") }
        every { userEntity.serialize() } returns userJson

        val result = TeamTask.serialize(task, userEntity)

        assertEquals("task123", result.get("_id").asString)
        assertEquals("rev123", result.get("_rev").asString)
        assertEquals("Task Title", result.get("title").asString)
        assertEquals(1620000000000L, result.get("deadline").asLong)
        assertEquals("Task Description", result.get("description").asString)
        assertTrue(result.get("completed").asBoolean)
        assertEquals(1620000000000L, result.get("completedTime").asLong)

        val assignee = result.getAsJsonObject("assignee")
        assertEquals("John Doe", assignee.get("userName").asString)

        val syncObj = result.getAsJsonObject("sync")
        assertEquals("syncValue", syncObj.get("syncKey").asString)

        val linkObj = result.getAsJsonObject("link")
        assertEquals("linkValue", linkObj.get("linkKey").asString)
    }

    @Test
    fun testSerialize_withoutUser() {
        val task = TeamTask().apply {
            _id = "" // Empty to test TextUtils.isEmpty(task._id) logic
            title = "Task Title"
            deadline = 1620000000000L
            description = "Task Description"
            completed = false
            completedTime = 0L
            sync = "{\"syncKey\":\"syncValue\"}"
            link = "{\"linkKey\":\"linkValue\"}"
        }

        val result = TeamTask.serialize(task, null)

        assertFalse(result.has("_id"))
        assertFalse(result.has("_rev"))
        assertEquals("Task Title", result.get("title").asString)
        assertEquals(1620000000000L, result.get("deadline").asLong)
        assertEquals("Task Description", result.get("description").asString)
        assertFalse(result.get("completed").asBoolean)
        assertEquals(0L, result.get("completedTime").asLong)

        assertEquals("", result.get("assignee").asString)

        val syncObj = result.getAsJsonObject("sync")
        assertEquals("syncValue", syncObj.get("syncKey").asString)

        val linkObj = result.getAsJsonObject("link")
        assertEquals("linkValue", linkObj.get("linkKey").asString)
    }
}
