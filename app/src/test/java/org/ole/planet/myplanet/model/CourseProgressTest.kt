package org.ole.planet.myplanet.model

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.NetworkUtils

class CourseProgressTest {

    @Before
    fun setup() {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "uniqueIdentifier"
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkUtils)
    }

    @Test
    fun testSerializeProgress() {
        val progress = CourseProgress().apply {
            userId = "user123"
            parentCode = "parentCode123"
            courseId = "courseId123"
            passed = true
            stepNum = 5
            createdOn = "2023-01-01"
            createdDate = 1672531200000L
            updatedDate = 1672617600000L
        }

        val jsonObject = CourseProgress.serializeProgress(progress)

        assertEquals("user123", jsonObject.get("userId").asString)
        assertEquals("parentCode123", jsonObject.get("parentCode").asString)
        assertEquals("courseId123", jsonObject.get("courseId").asString)
        assertEquals(true, jsonObject.get("passed").asBoolean)
        assertEquals(5, jsonObject.get("stepNum").asInt)
        assertEquals("2023-01-01", jsonObject.get("createdOn").asString)
        assertEquals(1672531200000L, jsonObject.get("createdDate").asLong)
        assertEquals(1672617600000L, jsonObject.get("updatedDate").asLong)
        assertEquals("uniqueIdentifier", jsonObject.get("androidId").asString)
        assertEquals("myplanet", jsonObject.get("app").asString)
    }
}
