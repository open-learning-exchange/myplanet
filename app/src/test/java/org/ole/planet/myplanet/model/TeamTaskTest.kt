package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TeamTaskTest {

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
        assertEquals(TaskStatus.COMPLETED.value, result.get("status").asString)

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
        assertEquals(TaskStatus.TODO.value, result.get("status").asString)

        assertEquals("", result.get("assignee").asString)

        val syncObj = result.getAsJsonObject("sync")
        assertEquals("syncValue", syncObj.get("syncKey").asString)

        val linkObj = result.getAsJsonObject("link")
        assertEquals("linkValue", linkObj.get("linkKey").asString)
    }

    @Test
    fun testSerialize_inProgressStatus() {
        val task = TeamTask().apply {
            _id = "t1"
            title = "In Progress Task"
            status = TaskStatus.IN_PROGRESS.value
            completed = false
        }

        val result = TeamTask.serialize(task, null)
        assertEquals(TaskStatus.IN_PROGRESS.value, result.get("status").asString)
        assertFalse(result.get("completed").asBoolean)
    }

    @Test
    fun testFromJson_withValidData() {
        val json = JsonObject().apply {
            addProperty("_id", "task123")
            addProperty("_rev", "rev1")
            addProperty("title", "Test Task")
            addProperty("status", "in-progress")
            addProperty("deadline", 1630000000L)
            addProperty("completedTime", 1630005000L)
            addProperty("description", "A very important task")
            addProperty("completed", true)

            val link = JsonObject().apply {
                addProperty("teams", "teamABC")
            }
            add("link", link)

            val sync = JsonObject().apply {
                addProperty("status", "synced")
            }
            add("sync", sync)

            val assignee = JsonObject().apply {
                addProperty("_id", "user456")
            }
            add("assignee", assignee)
        }

        val task = TeamTask.fromJson(json)

        assertEquals("task123", task.id)
        assertEquals("task123", task._id)
        assertEquals("rev1", task._rev)
        assertEquals("Test Task", task.title)
        assertEquals("in-progress", task.status)
        assertEquals(1630000000L, task.deadline)
        assertEquals(1630005000L, task.completedTime)
        assertEquals("A very important task", task.description)
        assertTrue(task.completed)
        assertEquals("{\"teams\":\"teamABC\"}", task.link)
        assertEquals("{\"status\":\"synced\"}", task.sync)
        assertEquals("teamABC", task.teamId)
        assertEquals("user456", task.assignee)
    }

    @Test
    fun testFromJson_withMissingAssigneeId() {
        val json = JsonObject().apply {
            val assignee = JsonObject().apply {
                addProperty("name", "John Doe")
                // _id is missing
            }
            add("assignee", assignee)
        }

        val task = TeamTask.fromJson(json)

        assertNull(task.assignee)
    }

    @Test
    fun testFromJson_withEmptyData() {
        val json = JsonObject()
        val task = TeamTask.fromJson(json)

        assertEquals("", task.id)
        assertEquals("", task._id)
        assertEquals("", task._rev)
        assertEquals("", task.title)
        assertEquals("", task.status)
        assertEquals(0L, task.deadline)
        assertEquals(0L, task.completedTime)
        assertEquals("", task.description)
        assertEquals("{}", task.link)
        assertEquals("{}", task.sync)
        assertEquals("", task.teamId)
        assertNull(task.assignee)
        assertFalse(task.completed)
    }
}
