package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

import io.mockk.mockk

class SyncTimeLoggerTest {

    @Test
    fun testExtractProcessName() {
        assertEquals("Courses", SyncTimeLogger.extractProcessName("courses"))
        assertEquals("Courses", SyncTimeLogger.extractProcessName("api/v1/courses"))
        assertEquals("Courses", SyncTimeLogger.extractProcessName("api/v1/courses?limit=10"))
        assertEquals("Courses", SyncTimeLogger.extractProcessName("api/v1/courses/"))
        assertEquals("Courses", SyncTimeLogger.extractProcessName("api//v1//courses//"))
        assertEquals("Api", SyncTimeLogger.extractProcessName("api"))
        assertEquals("Unknown", SyncTimeLogger.extractProcessName(""))
    }
}
