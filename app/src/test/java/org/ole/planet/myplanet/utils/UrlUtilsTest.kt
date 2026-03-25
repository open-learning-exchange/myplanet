package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun testDbUrl_withDbSuffix() {
        val input = "http://example.com/db"
        val expected = "http://example.com/db"
        val result = UrlUtils.dbUrl(input)
        assertEquals(expected, result)
    }

    @Test
    fun testDbUrl_withoutDbSuffix() {
        val input = "http://example.com"
        val expected = "http://example.com/db"
        val result = UrlUtils.dbUrl(input)
        assertEquals(expected, result)
    }

    @Test
    fun testDbUrl_withTrailingSlash() {
        val input = "http://example.com/"
        val expected = "http://example.com//db" // Given the current logic, this is what happens
        val result = UrlUtils.dbUrl(input)
        assertEquals(expected, result)
    }
}
