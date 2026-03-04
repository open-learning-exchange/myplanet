package org.ole.planet.myplanet.services.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServerUrlMapperTest {

    private lateinit var serverUrlMapper: ServerUrlMapper

    @Before
    fun setUp() {
        serverUrlMapper = ServerUrlMapper()
    }

    @Test
    fun processUrl_withUnmappedUrl_returnsNullAlternative() {
        val primaryUrl = "http://example.com/api"
        val expectedBaseUrl = "http://example.com"

        val result = serverUrlMapper.processUrl(primaryUrl)

        assertEquals(primaryUrl, result.primaryUrl)
        assertNull(result.alternativeUrl)
        assertEquals(expectedBaseUrl, result.extractedBaseUrl)
    }

    @Test
    fun processUrl_withInvalidUrl_returnsNullExtracted() {
        val primaryUrl = "invalid-url"
        val result = serverUrlMapper.processUrl(primaryUrl)

        assertEquals(primaryUrl, result.primaryUrl)
        assertNull(result.alternativeUrl)
        assertNull(result.extractedBaseUrl)
    }

    @Test
    fun processUrl_withHttps_returnsNullAlternative() {
        // Hardcoded IP that might be in BuildConfig but using https which isn't mapped
        val primaryUrl = "https://192.168.48.253/some/path"
        val expectedBaseUrl = "https://192.168.48.253"

        val result = serverUrlMapper.processUrl(primaryUrl)

        assertEquals(primaryUrl, result.primaryUrl)
        assertNull(result.alternativeUrl)
        assertEquals(expectedBaseUrl, result.extractedBaseUrl)
    }

    @Test
    fun processUrl_withPort_returnsCorrectBaseUrl() {
        val primaryUrl = "http://example.com:8080/path"
        val expectedBaseUrl = "http://example.com:8080"

        val result = serverUrlMapper.processUrl(primaryUrl)

        assertEquals(expectedBaseUrl, result.extractedBaseUrl)
    }
}
