package org.ole.planet.myplanet.services

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.UrlUtils

class FileUploaderTest {

    @Before
    fun setUp() {
        mockkObject(UrlUtils)
        every { UrlUtils.header } returns "Basic test_header"
    }

    @After
    fun tearDown() {
        io.mockk.unmockkObject(UrlUtils)
        unmockkAll()
    }

    @Test
    fun testGetHeaderMap() {
        val mimeType = "image/png"
        val rev = "1-testrev"

        val headerMap = FileUploader.getHeaderMap(mimeType, rev)

        assertEquals("Basic test_header", headerMap["Authorization"])
        assertEquals("image/png", headerMap["Content-Type"])
        assertEquals("1-testrev", headerMap["If-Match"])
        assertEquals(3, headerMap.size)
    }

    @Test
    fun testGetHeaderMap_withEmptyValues() {
        val mimeType = ""
        val rev = ""

        val headerMap = FileUploader.getHeaderMap(mimeType, rev)

        assertEquals("Basic test_header", headerMap["Authorization"])
        assertEquals("", headerMap["Content-Type"])
        assertEquals("", headerMap["If-Match"])
        assertEquals(3, headerMap.size)
    }
}
