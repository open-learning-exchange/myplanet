package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

import io.mockk.mockk

class SyncTimeLoggerTest {

    @Test
    fun testExtractProcessName() {
        val logger = SyncTimeLogger(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        assertEquals("Courses", logger.extractProcessName("courses"))
        assertEquals("Courses", logger.extractProcessName("api/v1/courses"))
        assertEquals("Courses", logger.extractProcessName("api/v1/courses?limit=10"))
        assertEquals("Courses", logger.extractProcessName("api/v1/courses/"))
        assertEquals("Courses", logger.extractProcessName("api//v1//courses//"))
        assertEquals("Api", logger.extractProcessName("api"))
        assertEquals("Unknown", logger.extractProcessName(""))
    }
}
