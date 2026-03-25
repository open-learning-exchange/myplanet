package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager

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
        val expected = "http://example.com/db"
        val result = UrlUtils.dbUrl(input)
        assertEquals(expected, result)
    }

    @Test
    fun testDbUrl_withSharedPrefManager_alternativeUrl() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns true
        every { spm.getProcessedAlternativeUrl() } returns "http://alt.example.com"

        val expected = "http://alt.example.com/db"
        val result = UrlUtils.dbUrl(spm)
        assertEquals(expected, result)
    }

    @Test
    fun testDbUrl_withSharedPrefManager_couchdbUrl() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns false
        every { spm.getCouchdbUrl() } returns "http://couch.example.com"

        val expected = "http://couch.example.com/db"
        val result = UrlUtils.dbUrl(spm)
        assertEquals(expected, result)
    }
}
