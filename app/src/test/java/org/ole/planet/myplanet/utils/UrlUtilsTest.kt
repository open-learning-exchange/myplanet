package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager

class UrlUtilsTest {

    private lateinit var sharedPrefManager: SharedPrefManager

    @Before
    fun setup() {
        sharedPrefManager = mockk(relaxed = true)
        every { sharedPrefManager.isAlternativeUrl() } returns false
        every { sharedPrefManager.getCouchdbUrl() } returns "http://example.com"
        every { sharedPrefManager.getProcessedAlternativeUrl() } returns "http://alternative.com"
    }

    @Test
    fun testGetHealthAccessUrl_withEmptyPin() {
        every { sharedPrefManager.getServerPin() } returns ""
        val url = UrlUtils.getHealthAccessUrl(sharedPrefManager)
        assertEquals("http://example.com/healthaccess?p=0000", url)
    }

    @Test
    fun testGetHealthAccessUrl_withCustomPin() {
        every { sharedPrefManager.getServerPin() } returns "1234"
        val url = UrlUtils.getHealthAccessUrl(sharedPrefManager)
        assertEquals("http://example.com/healthaccess?p=1234", url)
    }

    @Test
    fun testGetHealthAccessUrl_withAlternativeUrl() {
        every { sharedPrefManager.isAlternativeUrl() } returns true
        every { sharedPrefManager.getServerPin() } returns "5678"
        val url = UrlUtils.getHealthAccessUrl(sharedPrefManager)
        assertEquals("http://alternative.com/healthaccess?p=5678", url)
    }

    @Test
    fun testGetHealthAccessUrl_withDbSuffix() {
        every { sharedPrefManager.getCouchdbUrl() } returns "http://example.com/db"
        every { sharedPrefManager.getServerPin() } returns "4321"
        val url = UrlUtils.getHealthAccessUrl(sharedPrefManager)
        assertEquals("http://example.com/healthaccess?p=4321", url)
    }
}
