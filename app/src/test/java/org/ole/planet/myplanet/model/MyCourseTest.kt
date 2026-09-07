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
}
