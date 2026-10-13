package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MyCourseTest {

    @Test
    fun testSetUserId_nullOrBlankInput_doesNotModify() {
        val course = MyCourse(id = "c1", userId = listOf("user1", "user2"))
        course.setUserId(null)
        assertEquals(listOf("user1", "user2"), course.userId)

        course.setUserId("")
        assertEquals(listOf("user1", "user2"), course.userId)

        course.setUserId("   ")
        assertEquals(listOf("user1", "user2"), course.userId)
    }

    @Test
    fun testSetUserId_whenUserIdIsNull() {
        val course = MyCourse(id = "c1", userId = null)
        course.setUserId("user1")
        assertEquals(listOf("user1"), course.userId)
    }

    @Test
    fun testSetUserId_deduplicatesPreExistingAndPreservesInsertionOrder() {
        val course = MyCourse(id = "c1", userId = listOf("user1", "", "user2", "user1", "   "))
        course.setUserId("user3")
        assertEquals(listOf("user1", "user2", "user3"), course.userId)
    }

    @Test
    fun testSetUserId_existingUser_preservesInsertionOrderWithoutDuplicates() {
        val course = MyCourse(id = "c1", userId = listOf("user1", "user2", "user3"))
        course.setUserId("user2")
        assertEquals(listOf("user1", "user2", "user3"), course.userId)
    }

    @Test
    fun testRemoveUserId() {
        val course = MyCourse(id = "c1", userId = listOf("user1", "user2", "user3"))
        course.removeUserId("user2")
        assertEquals(listOf("user1", "user3"), course.userId)
    }

    @Test
    fun testSaveConcatenatedLinksToPrefs() {
        val spm = io.mockk.mockk<org.ole.planet.myplanet.services.SharedPrefManager>(relaxed = true)
        io.mockk.every { spm.getConcatenatedLinks() } returns "[\"http://example.com/link1\"]"

        val capturedJson = io.mockk.slot<String>()
        io.mockk.every { spm.setConcatenatedLinks(capture(capturedJson)) } returns Unit

        MyCourse.addConcatenatedLink("http://example.com/link2")
        MyCourse.addConcatenatedLink("http://example.com/link1")

        MyCourse.saveConcatenatedLinksToPrefs(spm)

        val savedSet = org.ole.planet.myplanet.utils.GsonUtils.gson.fromJson(
            capturedJson.captured,
            Array<String>::class.java
        ).toHashSet()

        assertEquals(
            setOf("http://example.com/link1", "http://example.com/link2"),
            savedSet
        )
    }

    @Test
    fun testSerialize_embedsStepsAndTheirResources() {
        val resource = MyLibrary().apply {
            _id = "resource1"
            title = "Resource One"
        }
        val course = MyCourse(
            id = "c1",
            courseId = "course1",
            courseRev = "1-abc",
            courseTitle = "Course One",
            description = "A course",
            memberLimit = 20
        )
        course.courseSteps = mutableListOf(
            CourseStep(id = "step1", stepTitle = "Step One", description = "First step")
        )

        val json = MyCourse.serialize(course, mapOf("step1" to listOf(resource)))

        assertEquals("course1", json.get("_id").asString)
        assertEquals("1-abc", json.get("_rev").asString)
        assertEquals("Course One", json.get("courseTitle").asString)
        assertEquals(20, json.get("memberLimit").asInt)

        val steps = json.getAsJsonArray("steps")
        assertEquals(1, steps.size())
        val step = steps[0].asJsonObject
        assertEquals("Step One", step.get("stepTitle").asString)
        assertEquals("step1", step.get("id").asString)

        val resources = step.getAsJsonArray("resources")
        assertEquals(1, resources.size())
        assertEquals("resource1", resources[0].asJsonObject.get("_id").asString)

        assertEquals(0, json.getAsJsonArray("images").size())
    }

    @Test
    fun testSerialize_stepWithNoMatchingResources_getsEmptyResourcesArray() {
        val course = MyCourse(id = "c1", courseId = "course1")
        course.courseSteps = mutableListOf(CourseStep(id = "step1", stepTitle = "Step One"))

        val json = MyCourse.serialize(course, emptyMap())

        val step = json.getAsJsonArray("steps")[0].asJsonObject
        assertEquals(0, step.getAsJsonArray("resources").size())
    }
}
